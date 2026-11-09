package com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolFixer
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.execute
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.util.getLsp4jRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.jsonrpc.messages.Either

/** Asks the server for its `source.fixAll` code action and applies the result. */
class OxlintFixer(private val project: Project) : ToolFixer {

  private val fixKind: OxlintFixKind
    get() = OxlintSettings.getInstance(project).state.fixKind

  override fun refusal(): OxcFixAllResult? =
    OxcFixAllResult.FixesTurnedOff.takeIf { fixKind == OxlintFixKind.NONE }

  override suspend fun fix(client: LspClient, file: VirtualFile, document: Document, timeoutMs: Long): Boolean {
    val (stamp, params) = readAction { document.modificationStamp to codeActionParams(client, file, document) }
    val results = withTimeout(timeoutMs) {
      val cached = client.sendRequest { it.textDocumentService.codeAction(params) }
      if (!cached.isNullOrEmpty()) return@withTimeout cached

      // The server only answers from a cache that didChange clears and pulling diagnostics refills,
      // and the daemon's pull is debounced, so saving right after typing finds it empty.
      pullDiagnostics(client, file)
      client.sendRequest { it.textDocumentService.codeAction(params) }
    }

    val actions = preferredActions(client, results)
    if (actions.isEmpty()) return false

    // Suspends instead of blocking a thread, so a cancel stops it rather than being swallowed.
    return WriteCommandAction.writeCommandAction(project)
      .withName(OxcBackendBundle.message("oxlint.run.quickfix"))
      .withGroupId("Oxc")
      .execute {
        // The edits are positions in the text that the server saw.
        if (document.modificationStamp != stamp) {
          thisLogger().info("Not fixing ${file.path}, because it changed while waiting for the server")
          return@execute false
        }
        actions.forEach { it.invoke(file) }
        document.modificationStamp != stamp
      }
  }

  private fun codeActionParams(client: LspClient, file: VirtualFile, document: Document): CodeActionParams {
    val kind = if (fixKind.isDangerous) "source.fixAllDangerous.oxc" else "source.fixAll.oxc"
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
}

private val OxlintFixKind.isDangerous: Boolean
  get() = this == OxlintFixKind.DANGEROUS_FIX ||
          this == OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION ||
          this == OxlintFixKind.ALL
