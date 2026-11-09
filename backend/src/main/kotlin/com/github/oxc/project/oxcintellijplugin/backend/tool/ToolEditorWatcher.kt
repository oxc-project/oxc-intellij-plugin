package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState

/**
 * Stops a tool's servers once no open file needs them, in any connected client. Opening a supported
 * file starts them again. In a monorepo, a server whose root has no open file left stops while the
 * servers of other roots keep running.
 */
// DevKit's inspection lists this listener as frontend API, but JetBrains' API page
// https://plugins.jetbrains.com/docs/intellij/frontend-backend-shared-apis.html lists it as shared
// and leaves the decision to the plugin.
@Suppress("SplitModeApiUsage", "UnstableApiUsage")
class ToolEditorWatcher : FileEditorManagerListener {

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    val manager = LspClientManager.getInstance(source.project)
    for (definition in ToolDefinition.all) {
      // openFiles only lists the current client's files, and each client's close runs as that client.
      val openFiles = source.openFilesWithRemotes
      if (openFiles.none(definition.settings(source.project)::isSupported)) {
        manager.stopClients(definition.providerClass)
        continue
      }

      val live = manager.getClients(definition.providerClass).filter(::isLive)
      val (needed, idle) = live.partition { client -> openFiles.any(client.descriptor::isSupportedFile) }
      if (idle.isEmpty()) continue

      // The platform can only stop all of a provider's servers, so the ones open files still need start
      // again from their own descriptors. On the EDT, the stop doesn't wait for a server still starting.
      manager.stopClients(definition.providerClass)
      needed.forEach { manager.ensureClientStarted(definition.providerClass, it.descriptor) }
    }
  }

  private fun isLive(client: LspClient): Boolean =
    client.state == LspServerState.Initializing || client.state == LspServerState.Running
}
