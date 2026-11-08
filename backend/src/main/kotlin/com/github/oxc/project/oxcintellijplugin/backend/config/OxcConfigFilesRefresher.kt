package com.github.oxc.project.oxcintellijplugin.backend.config

import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project

/** A manual config path decides which file gets the icon, so a change shows straight away. */
class OxcConfigFilesRefresher(private val project: Project) : OxcSettingsListener {

  override fun settingsChanged(tool: OxcTool, affectsServers: Boolean) {
    if (!affectsServers || project.isDefault) return

    ApplicationManager.getApplication().invokeLater({
      ProjectView.getInstance(project).refresh()
      // In split mode the editors are on the client, so the backend's own list of open files is empty. A
      // thin client's tab icon still only changes when the tab is reopened, because in 2026.3 the backend
      // sends the client a tab's title and tooltip, but not its icon. The project view does update.
      val editors = FileEditorManagerEx.getInstanceEx(project)
      editors.openFilesWithRemotes.forEach(editors::updateFilePresentation)
    }, project.disposed)
  }
}
