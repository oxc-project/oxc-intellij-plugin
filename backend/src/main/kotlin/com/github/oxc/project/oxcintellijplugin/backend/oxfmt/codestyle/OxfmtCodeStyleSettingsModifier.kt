package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import java.util.function.Consumer

/** Overlays the nearest Oxfmt config on a file's code style, without changing the project's own settings. */
@Suppress("UnstableApiUsage")
class OxfmtCodeStyleSettingsModifier : CodeStyleSettingsModifier {

  override fun modifySettings(settings: TransientCodeStyleSettings, psiFile: PsiFile): Boolean {
    val project = psiFile.project
    val file = psiFile.virtualFile ?: return false
    if (project.isDisposed || !ApplicationManager.getApplication().isReadAccessAllowed) return false

    val oxfmt = OxfmtSettings.getInstance(project)
    if (!oxfmt.state.preferOxfmtCodeStyleSettings || !oxfmt.isSupported(file)) return false

    val config = findOxfmtConfig(project, file)?.let { OxfmtConfig.read(project, it) } ?: return false
    return OxfmtCodeStyle.apply(settings, config, psiFile.language)
  }

  override fun getName(): String = OxcBackendBundle.message("oxfmt.code.style.display.name")

  override fun getStatusBarUiContributor(transientSettings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor =
    OxfmtCodeStyleStatusBarUiContributor()

  override fun mayOverrideSettingsOf(project: Project): Boolean =
    OxfmtSettings.getInstance(project).state.preferOxfmtCodeStyleSettings

  override fun getDisablingFunction(project: Project): Consumer<CodeStyleSettings?> = Consumer {
    disableOxfmtCodeStyle(project)
  }
}

fun disableOxfmtCodeStyle(project: Project) {
  val settings = OxfmtSettings.getInstance(project)
  settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = false))
  CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
}
