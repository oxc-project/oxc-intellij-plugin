package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import java.util.function.Consumer

/**
 * Overlays the nearest Oxfmt config on a file's code style, without changing the project's own settings.
 * A JavaScript or TypeScript config is read through Node in the background, so its overlay comes once
 * that's done.
 */
@Suppress("UnstableApiUsage")
class OxfmtCodeStyleSettingsModifier : CodeStyleSettingsModifier {

  override fun modifySettings(settings: TransientCodeStyleSettings, psiFile: PsiFile): Boolean {
    val project = psiFile.project
    // Settings for New Projects can turn the overlay on there, and its preview asks for a file in it.
    if (project.isDefault) return false
    val file = psiFile.virtualFile ?: return false
    if (project.isDisposed || !ApplicationManager.getApplication().isReadAccessAllowed) return false

    val oxfmt = OxfmtSettings.getInstance(project)
    if (!oxfmt.state.preferOxfmtCodeStyleSettings || !oxfmt.isSupported(file)) return false

    val config = locateOxfmtConfig(project, file)?.let { readOxfmtConfigNow(project, it) } ?: return false
    // Notifying alone didn't reach a file's cached overlay after Import, so the refresh also bumps this.
    settings.addDependency(OxfmtOverlayTracker.getInstance(project))
    return OxfmtCodeStyle.apply(settings, config, psiFile.language)
  }

  override fun getName(): String = OxcBackendBundle.message("oxfmt.code.style.display.name")

  override fun getStatusBarUiContributor(transientSettings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor =
    OxfmtCodeStyleStatusBarUiContributor()

  /** Never for the default project, which has no files of its own and no LSP manager to find a config with. */
  override fun mayOverrideSettingsOf(project: Project): Boolean =
    !project.isDefault && OxfmtSettings.getInstance(project).state.preferOxfmtCodeStyleSettings

  override fun getDisablingFunction(project: Project): Consumer<CodeStyleSettings?> = Consumer {
    disableOxfmtCodeStyle(project)
  }
}

fun disableOxfmtCodeStyle(project: Project) {
  val settings = OxfmtSettings.getInstance(project)
  settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = false))
  refreshOxfmtOverlay(project)
}

/** JSON is read now, and a JavaScript or TypeScript config once Node has read it. */
private fun readOxfmtConfigNow(project: Project, location: OxfmtConfigLocation): OxfmtConfig? = when (location.format) {
  OxfmtConfigFormat.JSON -> OxfmtConfig.read(project, location.file)
  OxfmtConfigFormat.UNREADABLE -> null
  OxfmtConfigFormat.JAVASCRIPT, OxfmtConfigFormat.VITE_PLUS ->
    (OxfmtJsConfigLoader.getInstance(project).getNow(location) as? OxfmtJsConfigResult.Loaded)?.config
}
