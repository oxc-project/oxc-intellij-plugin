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

/**
 * Writes the nearest config into the project's own code style, which is stored in `.idea/codeStyles`. A
 * JavaScript or TypeScript config is read through Node, in a trusted project only.
 */
suspend fun importOxfmtCodeStyle(project: Project, file: VirtualFile): OxfmtImportResult {
  val location = readAction { locateOxfmtConfig(project, file) } ?: return OxfmtImportResult.CONFIG_NOT_FOUND
  return importOxfmtCodeStyle(project, location, asked = true).result
}

/** What an import did, and [source], the file whose options it used, which under `vp` can be a fallback. */
class OxfmtImport(val result: OxfmtImportResult, val source: VirtualFile? = null)

/**
 * Writes the config at [location] into the project's own code style. An automatic import, which nobody
 * [asked] for, doesn't repeat a problem reading a JavaScript or TypeScript config that was already shown.
 */
suspend fun importOxfmtCodeStyle(project: Project, location: OxfmtConfigLocation, asked: Boolean): OxfmtImport {
  val (source, config) = when (location.format) {
    OxfmtConfigFormat.JSON -> location.file to (readAction { OxfmtConfig.read(project, location.file) }
                                                ?: return OxfmtImport(OxfmtImportResult.CONFIG_NOT_READABLE))
    OxfmtConfigFormat.UNREADABLE -> return OxfmtImport(OxfmtImportResult.CONFIG_NOT_READABLE)
    OxfmtConfigFormat.JAVASCRIPT, OxfmtConfigFormat.VITE_PLUS -> when (val result = OxfmtJsConfigLoader.getInstance(project).load(location, asked)) {
      is OxfmtJsConfigResult.Loaded -> result.file to result.config
      OxfmtJsConfigResult.Missing -> return OxfmtImport(OxfmtImportResult.CONFIG_NOT_FOUND)
      // The loader has shown why.
      is OxfmtJsConfigResult.Untrusted, is OxfmtJsConfigResult.Failed -> return OxfmtImport(OxfmtImportResult.CONFIG_NOT_LOADED)
    }
  }

  return withContext(Dispatchers.EDT) {
    if (OxfmtCodeStyle.isApplied(CodeStyle.getSettings(project), config)) return@withContext OxfmtImport(OxfmtImportResult.ALREADY_IMPORTED, source)

    val manager = CodeStyleSettingsManager.getInstance(project)
    if (!manager.USE_PER_PROJECT_SETTINGS) {
      // Keeps the change in this project, rather than in the IDE-wide scheme that it was using.
      CodeStyle.setMainProjectSettings(project, manager.cloneSettings(CodeStyle.getSettings(project)))
    }
    val settings = manager.mainProjectCodeStyle!!
    OxfmtCodeStyle.apply(settings, config)
    // The store only writes the code style when this count has changed.
    settings.modificationTracker.incModificationCount()
    refreshOxfmtOverlay(project)
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
    OxfmtImport(OxfmtImportResult.IMPORTED, source)
  }
}
