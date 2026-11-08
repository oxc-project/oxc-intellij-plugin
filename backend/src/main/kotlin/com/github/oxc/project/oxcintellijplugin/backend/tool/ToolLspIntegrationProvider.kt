package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider

abstract class ToolLspIntegrationProvider(private val definition: ToolDefinition) : LspIntegrationProvider {

  override fun fileOpened(
    project: Project,
    file: VirtualFile,
    clientStarter: LspIntegrationProvider.LspClientStarter,
  ) {
    definition.createDescriptor(project, file)?.let(clientStarter::ensureClientStarted)
  }
}
