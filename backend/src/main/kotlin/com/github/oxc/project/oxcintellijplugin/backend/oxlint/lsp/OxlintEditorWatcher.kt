package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.isOxlintSupportedFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager

/**
 * Stops the servers once no open file needs them. Opening a supported file starts them again.
 */
// DevKit's inspection lists this listener as frontend API, but JetBrains' API page
// https://plugins.jetbrains.com/docs/intellij/frontend-backend-shared-apis.html lists it as shared
// and leaves the decision to the plugin.
@Suppress("SplitModeApiUsage")
class OxlintEditorWatcher : FileEditorManagerListener {

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    if (source.openFiles.none(::isOxlintSupportedFile)) {
      LspClientManager.getInstance(source.project)
        .stopClients(OxlintLspIntegrationProvider::class.java)
    }
  }
}
