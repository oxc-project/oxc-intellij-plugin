package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettingsState
import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.util.PathUtil

/**
 * Makes files pick up the overlay again. A config change only refreshes it, and never changes the
 * project's own code style, which only the Import action does.
 */
class OxfmtCodeStyleRefresher(private val project: Project) : BulkFileListener, OxcSettingsListener {

  override fun after(events: List<VFileEvent>) {
    val settings = OxfmtSettings.getInstance(project).state
    if (!settings.preferOxfmtCodeStyleSettings) return
    val roots = roots()
    val manual = manualConfigPaths(settings, roots)
    if (events.any { touchesConfig(it, roots, manual) }) refresh()
  }

  override fun settingsChanged(tool: OxcTool, affectsServers: Boolean) {
    if (tool == OxcTool.OXFMT && !project.isDefault) refresh()
  }

  /**
   * A config in the project or in a folder above it can change which config a file uses. So can any
   * config's name, because a TypeScript config can hide a JSON one. Under `vp`, the configs are
   * `vite.config.*`. The manual config can have any name.
   */
  private fun touchesConfig(event: VFileEvent, roots: List<String>, manual: List<String>): Boolean {
    val paths = listOfNotNull(
      event.path,
      (event as? VFilePropertyChangeEvent)?.takeIf { it.isRename }?.newPath,
      (event as? VFileMoveEvent)?.newPath,
    )
    return paths.any { path ->
      manual.any { FileUtil.pathsEqual(path, it) } ||
      (PathUtil.getFileName(path).let { it in OXFMT_CONFIG_NAMES || it in VITE_CONFIG_NAMES } && roots.any { root ->
        FileUtil.isAncestor(root, path, false) || FileUtil.isAncestor(PathUtil.getParentPath(path), root, false)
      })
    }
  }

  /** Where a server could run from: the project's folders, and the roots of the servers that do run. */
  private fun roots(): List<String> =
    (listOfNotNull(project.basePath) +
     ProjectRootManager.getInstance(project).contentRoots.map { it.path } +
     LspClientManager.getInstance(project).getClients(OxfmtDefinition.providerClass)
       .mapNotNull { (it.descriptor as? ToolLspClientDescriptor)?.launch?.root?.path })
      .distinct()

  /**
   * The files the manual config can be, resolved from each root as the lookup resolves it, so that
   * `./configs/team.json` matches and a `team.json` in another folder doesn't.
   */
  private fun manualConfigPaths(settings: OxfmtSettingsState, roots: List<String>): List<String> {
    if (settings.configurationMode != ConfigurationMode.MANUAL) return emptyList()
    val path = settings.configPath?.trim()?.takeIf { it.isNotEmpty() }?.let { resolveSettingPath(project, it) }
               ?: return emptyList()
    return roots.mapNotNull { manualConfigPath(path, it) }.distinct()
  }

  private fun refresh() {
    ApplicationManager.getApplication().invokeLater({ refreshOxfmtOverlay(project) }, project.disposed)
  }
}
