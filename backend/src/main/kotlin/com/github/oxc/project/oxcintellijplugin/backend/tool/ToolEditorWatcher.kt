package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager

/**
 * Stops a tool's servers once no open file needs them, in any connected client. Opening a supported
 * file starts them again.
 */
// DevKit's inspection lists this listener as frontend API, but JetBrains' API page
// https://plugins.jetbrains.com/docs/intellij/frontend-backend-shared-apis.html lists it as shared
// and leaves the decision to the plugin.
@Suppress("SplitModeApiUsage", "UnstableApiUsage")
class ToolEditorWatcher : FileEditorManagerListener {

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    for (definition in ToolDefinition.all) {
      val settings = definition.settings(source.project)
      // openFiles only lists the current client's files, and each client's close runs as that client.
      if (source.openFilesWithRemotes.none(settings::isSupported)) {
        LspClientManager.getInstance(source.project).stopClients(definition.providerClass)
      }
    }
  }
}
