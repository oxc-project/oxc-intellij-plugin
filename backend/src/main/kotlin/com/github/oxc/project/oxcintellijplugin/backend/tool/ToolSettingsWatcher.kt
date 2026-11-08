package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager

/** Settings are sent to the servers when they start, so a change only applies through a restart. */
class ToolSettingsWatcher(private val project: Project) : OxcSettingsListener {

  override fun settingsChanged(tool: OxcTool, affectsServers: Boolean) {
    if (!affectsServers || project.isDefault) return

    ApplicationManager.getApplication().invokeLater({
      LspClientManager.getInstance(project)
        .stopAndRestartClientsIfNeeded(ToolDefinition.of(tool).providerClass)
    }, project.disposed)
  }
}
