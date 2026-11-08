package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.util.PathUtil

/**
 * Makes files pick up the overlay again. A config change only refreshes it, and never changes the
 * project's own code style, which only the Import action does.
 */
class OxfmtCodeStyleRefresher(private val project: Project) : BulkFileListener, OxcSettingsListener {

  override fun after(events: List<VFileEvent>) {
    if (!OxfmtSettings.getInstance(project).state.preferOxfmtCodeStyleSettings) return
    if (events.any(::touchesConfig)) refresh()
  }

  override fun settingsChanged(tool: OxcTool, affectsServers: Boolean) {
    if (tool == OxcTool.OXFMT && !project.isDefault) refresh()
  }

  /**
   * A config in the project or in a folder above it can change which config a file uses. So can any
   * config's name, because a TypeScript config can hide a JSON one. The manual config can have any name.
   */
  private fun touchesConfig(event: VFileEvent): Boolean {
    val roots = listOfNotNull(project.basePath) + ProjectRootManager.getInstance(project).contentRoots.map { it.path }
    val manual = OxfmtSettings.getInstance(project).state.configPath?.trim()?.takeIf { it.isNotEmpty() }
      ?.let { FileUtil.toSystemIndependentName(resolveSettingPath(project, it)) }
    val paths = listOfNotNull(
      event.path,
      (event as? VFilePropertyChangeEvent)?.takeIf { it.isRename }?.newPath,
      (event as? VFileMoveEvent)?.newPath,
    )
    return paths.any { path ->
      (manual != null && (path.endsWith("/$manual") || FileUtil.pathsEqual(path, manual))) ||
      (PathUtil.getFileName(path) in OXFMT_CONFIG_NAMES && roots.any { root ->
        FileUtil.isAncestor(root, path, false) || FileUtil.isAncestor(PathUtil.getParentPath(path), root, false)
      })
    }
  }

  private fun refresh() {
    ApplicationManager.getApplication().invokeLater({
      CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
    }, project.disposed)
  }
}
