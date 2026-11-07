package com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.util.getLsp4jRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.jsonrpc.messages.Either

private const val FIX_ALL_TIMEOUT_REGISTRY_KEY = "oxc.lint.fix.all.timeout.ms"
private const val DEFAULT_FIX_ALL_TIMEOUT_MS = 30_000

/** Asks the server for its `source.fixAll` code action and applies the result. */
@Service(Service.Level.PROJECT)
class OxlintFixAll(private val project: Project) {

  private val providerClass = OxlintLspIntegrationProvider::class.java

  /** Fixes a file for the action. A closed file is saved afterwards, because nothing else would save it. */
  suspend fun fixFile(file: VirtualFile): OxlintFixAllResult {
    val settings = OxlintSettings.getInstance(project)
    if (!settings.isSupported(file)) return OxlintFixAllResult.UnsupportedFile
    if (!settings.isEnabled) return OxlintFixAllResult.NothingToApply
    if (settings.state.fixKind == OxlintFixKind.NONE) return OxlintFixAllResult.FixesTurnedOff

    val document = readAction { FileDocumentManager.getInstance().getDocument(file) }
                   ?: return OxlintFixAllResult.NothingToApply
    return try {
      withTimeout(timeoutMs()) {
        val isOpen = isOpenInAnyClient(file)
        // The server reads a file that isn't open from disk, so it has to match the document.
        if (!isOpen) saveDocument(document)
        if (!startServer(file)) return@withTimeout OxlintFixAllResult.OxlintNotFound

        val applied = fixDocument(file, document)
        if (!isOpen) {
          if (applied) saveDocument(document)
          stopServersThatNoOpenFileNeeds()
        }
        if (applied) OxlintFixAllResult.Applied else OxlintFixAllResult.NothingToApply
      }
    }
    catch (_: TimeoutCancellationException) {
      OxlintFixAllResult.TimedOut
    }
    catch (exception: CancellationException) {
      throw exception
    }
    catch (exception: Exception) {
      thisLogger().warn("Failed to apply Oxlint fixes to ${file.path}", exception)
      OxlintFixAllResult.Failed(exception.message ?: exception.toString())
    }
  }

  /**
   * Fixes a document, leaving it unsaved. Returns false if there was nothing to do. The timeout is per
   * file so that one slow file gets skipped, rather than using up the time of a whole Save All.
   */
  suspend fun fixDocument(file: VirtualFile, document: Document): Boolean {
    if (OxlintSettings.getInstance(project).state.fixKind == OxlintFixKind.NONE) return false
    val client = findClient(file) ?: return false

    val params = codeActionParams(client, file, document)
    val results = withTimeout(timeoutMs()) {
      val cached = client.sendRequest { it.textDocumentService.codeAction(params) }
      if (!cached.isNullOrEmpty()) return@withTimeout cached

      // The server only answers from a cache that didChange clears and pulling diagnostics refills,
      // and the daemon's pull is debounced, so saving right after typing finds it empty.
      pullDiagnostics(client, file)
      client.sendRequest { it.textDocumentService.codeAction(params) }
    }

    val actions = preferredActions(client, results)
    if (actions.isEmpty()) return false

    WriteCommandAction.runWriteCommandAction(project, OxcBackendBundle.message("oxlint.run.quickfix"), "Oxc", {
      actions.forEach { it.invoke(file) }
    })
    return true
  }

  private fun codeActionParams(client: LspClient, file: VirtualFile, document: Document): CodeActionParams {
    val kind = if (OxlintSettings.getInstance(project).state.fixKind.isDangerous) {
      "source.fixAllDangerous.oxc"
    }
    else {
      "source.fixAll.oxc"
    }
    return CodeActionParams(
      client.getDocumentIdentifier(file),
      getLsp4jRange(document, 0, document.textLength),
      CodeActionContext().apply {
        diagnostics = emptyList()
        only = listOf(kind)
        triggerKind = CodeActionTriggerKind.Invoked
      },
    )
  }

  private suspend fun pullDiagnostics(client: LspClient, file: VirtualFile) {
    try {
      val params = DocumentDiagnosticParams(client.getDocumentIdentifier(file))
      client.sendRequest { it.textDocumentService.diagnostic(params) }
    }
    catch (exception: CancellationException) {
      throw exception
    }
    catch (exception: Exception) {
      // A server without pull diagnostics rejects this, and keeps its cache filled by itself.
      thisLogger().debug("Couldn't pull diagnostics before asking for Oxlint fixes", exception)
    }
  }

  private suspend fun preferredActions(
    client: LspClient,
    results: List<Either<Command, CodeAction>>?,
  ): List<LspIntentionAction> = results.orEmpty()
    .filter { it.isRight && it.right.isPreferred == true }
    .map { LspIntentionAction(client, it.right) }
    .filter { readAction { it.isAvailable() } }

  private fun findClient(file: VirtualFile): LspClient? =
    LspClientManager.getInstance(project).getClients(providerClass)
      .firstOrNull { it.descriptor.isSupportedFile(file) && it.state == LspServerState.Running }

  /** Waits for the file's server, starting it if needed. Returns false if there's nothing to run. */
  private suspend fun startServer(file: VirtualFile): Boolean {
    if (findClient(file) != null) return true

    val descriptor = readAction { OxlintLspIntegrationProvider().createDescriptor(project, file) } ?: return false
    LspClientManager.getInstance(project).ensureClientStarted(providerClass, descriptor)
    while (findClient(file) == null) {
      val failed = LspClientManager.getInstance(project).getClients(providerClass)
        .any { it.descriptor.isSupportedFile(file) && it.state == LspServerState.ShutdownUnexpectedly }
      if (failed) throw ExecutionException(OxcBackendBundle.message("oxlint.server.failed.to.start"))
      delay(50)
    }
    return true
  }

  private fun isOpenInAnyClient(file: VirtualFile): Boolean =
    file in FileEditorManager.getInstance(project).openFilesWithRemotes

  private suspend fun saveDocument(document: Document) {
    withContext(Dispatchers.EDT) { FileDocumentManager.getInstance().saveDocument(document) }
  }

  private fun stopServersThatNoOpenFileNeeds() {
    val settings = OxlintSettings.getInstance(project)
    if (FileEditorManager.getInstance(project).openFilesWithRemotes.none(settings::isSupported)) {
      LspClientManager.getInstance(project).stopClients(providerClass)
    }
  }

  private fun timeoutMs(): Long {
    val value = Registry.intValue(FIX_ALL_TIMEOUT_REGISTRY_KEY, DEFAULT_FIX_ALL_TIMEOUT_MS)
    return (if (value > 0) value else DEFAULT_FIX_ALL_TIMEOUT_MS).toLong()
  }

  companion object {
    fun getInstance(project: Project): OxlintFixAll = project.service()
  }
}

private val OxlintFixKind.isDangerous: Boolean
  get() = this == OxlintFixKind.DANGEROUS_FIX ||
          this == OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION ||
          this == OxlintFixKind.ALL
