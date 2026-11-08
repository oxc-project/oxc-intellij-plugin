package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
import com.intellij.application.options.CodeStyle
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes the nearest config into the project's own code style, which is stored in `.idea/codeStyles`. */
suspend fun importOxfmtCodeStyle(project: Project, file: VirtualFile): OxfmtImportResult {
  val location = readAction { locateOxfmtConfig(project, file) } ?: return OxfmtImportResult.CONFIG_NOT_FOUND
  val config = location.takeIf { it.format == OxfmtConfigFormat.JSON }
                 ?.let { readAction { OxfmtConfig.read(project, it.file) } } ?: return OxfmtImportResult.CONFIG_NOT_READABLE

  return withContext(Dispatchers.EDT) {
    if (OxfmtCodeStyle.isApplied(CodeStyle.getSettings(project), config)) return@withContext OxfmtImportResult.ALREADY_IMPORTED

    val manager = CodeStyleSettingsManager.getInstance(project)
    if (!manager.USE_PER_PROJECT_SETTINGS) {
      // Keeps the change in this project, rather than in the IDE-wide scheme that it was using.
      CodeStyle.setMainProjectSettings(project, manager.cloneSettings(CodeStyle.getSettings(project)))
    }
    val settings = manager.mainProjectCodeStyle!!
    OxfmtCodeStyle.apply(settings, config)
    // The store only writes the code style when this count has changed.
    settings.modificationTracker.incModificationCount()
    manager.notifyCodeStyleSettingsChanged()
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
    OxfmtImportResult.IMPORTED
  }
}
