package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem

abstract class ToolLspIntegrationProvider(private val definition: ToolDefinition) : LspIntegrationProvider {

  override fun fileOpened(
    project: Project,
    file: VirtualFile,
    clientStarter: LspIntegrationProvider.LspClientStarter,
  ) {
    definition.createDescriptor(project, file)?.let(clientStarter::ensureClientStarted)
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
    ToolWidgetItem(lspClient, currentFile, definition)
}
