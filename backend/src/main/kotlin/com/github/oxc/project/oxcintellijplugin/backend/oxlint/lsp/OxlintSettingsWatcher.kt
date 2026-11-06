package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager

/** Settings are sent to the servers when they start, so a change only applies through a restart. */
class OxlintSettingsWatcher(private val project: Project) : OxlintSettingsListener {

  override fun settingsChanged() {
    if (project.isDefault) return

    ApplicationManager.getApplication().invokeLater({
      LspClientManager.getInstance(project)
        .stopAndRestartClientsIfNeeded(OxlintLspIntegrationProvider::class.java)
    }, project.disposed)
  }
}
