package com.github.oxc.project.oxcintellijplugin.testutil

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettingsState
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsState
import com.intellij.openapi.project.Project

/**
 * Puts both tools' settings back to their defaults. A light test project is shared by every light test
 * class, and so are its settings, so a class that changes them resets both afterwards, whichever tool it
 * tests, and a class that relies on the defaults resets them first.
 */
fun resetOxcSettings(project: Project) {
  OxlintSettings.getInstance(project).loadState(OxlintSettingsState())
  OxfmtSettings.getInstance(project).loadState(OxfmtSettingsState())
}
