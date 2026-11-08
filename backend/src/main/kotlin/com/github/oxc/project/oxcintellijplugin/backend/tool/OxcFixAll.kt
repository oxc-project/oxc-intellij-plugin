package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val DEFAULT_FIX_ALL_TIMEOUT_MS = 30_000

/** Runs a tool's change on a file: starting its server if needed, and handling timeouts and failures. */
@Service(Service.Level.PROJECT)
class OxcFixAll(private val project: Project) {

  /** Fixes a file for the action. A closed file is saved afterwards, because nothing else would save it. */
  suspend fun fixFile(definition: ToolDefinition, file: VirtualFile): OxcFixAllResult {
    val settings = definition.settings(project)
    if (!settings.isSupported(file)) return OxcFixAllResult.UnsupportedFile
    if (!settings.isEnabled) return OxcFixAllResult.NothingToApply
    definition.fixer(project).refusal()?.let { return it }

    val isOpen = isOpenInAnyClient(file)
    return try {
      withTimeout(timeoutMs(definition)) {
        // In the timeout, because a refresh over `\\wsl$` or a remote file system can take any time.
        if (!isOpen) refreshUnlessModified(file)
        val document = readAction { FileDocumentManager.getInstance().getDocument(file) }
                       ?: return@withTimeout OxcFixAllResult.NothingToApply
        // The server reads a file that isn't open from disk, so it has to match the document.
        if (!isOpen) saveDocument(document)
        if (!startServer(definition, file)) return@withTimeout OxcFixAllResult.ToolNotFound

        val applied = fixDocument(definition, file, document)
        if (!isOpen) {
          if (applied) saveDocument(document)
          stopServersThatNoOpenFileNeeds(definition)
        }
        if (applied) OxcFixAllResult.Applied else OxcFixAllResult.NothingToApply
      }
    }
    catch (_: TimeoutCancellationException) {
      OxcFixAllResult.TimedOut
    }
    catch (exception: CancellationException) {
      throw exception
    }
    catch (exception: Exception) {
      thisLogger().warn("Failed to apply ${definition.displayName} to ${file.path}", exception)
      OxcFixAllResult.Failed(exception.message ?: exception.toString())
    }
  }

  /**
   * Fixes a document, leaving it unsaved. Returns false if there was nothing to do. The timeout is per
   * file so that one slow file gets skipped, rather than using up the time of a whole Save All.
   */
  suspend fun fixDocument(definition: ToolDefinition, file: VirtualFile, document: Document): Boolean {
    val fixer = definition.fixer(project)
    if (fixer.refusal() != null) return false
    val client = findClient(definition, file) ?: return false

    return fixer.fix(client, file, document, timeoutMs(definition))
  }

  private fun findClient(definition: ToolDefinition, file: VirtualFile): LspClient? =
    LspClientManager.getInstance(project).getClients(definition.providerClass)
      .firstOrNull { it.descriptor.isSupportedFile(file) && it.state == LspServerState.Running }

  /** Waits for the file's server, starting it if needed. Returns false if there's nothing to run. */
  private suspend fun startServer(definition: ToolDefinition, file: VirtualFile): Boolean {
    if (findClient(definition, file) != null) return true

    val provider = definition.providerClass
    val descriptor = readAction { definition.createDescriptor(project, file) } ?: return false
    LspClientManager.getInstance(project).ensureClientStarted(provider, descriptor)
    while (findClient(definition, file) == null) {
      val failed = LspClientManager.getInstance(project).getClients(provider)
        .any { it.descriptor.isSupportedFile(file) && it.state == LspServerState.ShutdownUnexpectedly }
      if (failed) {
        throw ExecutionException(OxcBackendBundle.message("${definition.messagePrefix}.server.failed.to.start"))
      }
      delay(50)
    }
    return true
  }

  private fun isOpenInAnyClient(file: VirtualFile): Boolean =
    file in FileEditorManager.getInstance(project).openFilesWithRemotes

  /**
   * Picks up a change made outside the IDE that the file watcher hasn't reported yet, such as a checkout
   * just before the action. Otherwise the document keeps the old text, the server fixes the new text on
   * disk, and saving the result conflicts with the disk. A file with unsaved changes keeps them.
   */
  private suspend fun refreshUnlessModified(file: VirtualFile) {
    if (readAction { FileDocumentManager.getInstance().isFileModified(file) }) return
    val dirty = VfsUtil.markDirty(false, false, file)
    // Suspends instead of blocking a thread, so the timeout can cancel it. It must run outside a read action.
    if (dirty.isNotEmpty()) RefreshQueue.getInstance().refresh(false, dirty)
  }

  private suspend fun saveDocument(document: Document) {
    withContext(Dispatchers.EDT) { FileDocumentManager.getInstance().saveDocument(document) }
  }

  private fun stopServersThatNoOpenFileNeeds(definition: ToolDefinition) {
    val settings = definition.settings(project)
    if (FileEditorManager.getInstance(project).openFilesWithRemotes.none(settings::isSupported)) {
      LspClientManager.getInstance(project).stopClients(definition.providerClass)
    }
  }

  private fun timeoutMs(definition: ToolDefinition): Long {
    val value = Registry.intValue(definition.fixAllTimeoutRegistryKey, DEFAULT_FIX_ALL_TIMEOUT_MS)
    return (if (value > 0) value else DEFAULT_FIX_ALL_TIMEOUT_MS).toLong()
  }

  companion object {
    fun getInstance(project: Project): OxcFixAll = project.service()
  }
}
