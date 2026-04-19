package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings


interface OxfmtCodeStyleInstaller {

    fun install(project: Project, config: OxfmtConfig, settings: CodeStyleSettings)

    fun isInstalled(project: Project, config: OxfmtConfig, settings: CodeStyleSettings): Boolean

    companion object {

        fun applyCommonOxfmtSettings(config: OxfmtConfig, settings: CodeStyleSettings,
            language: Language) {
            val commonSettings = settings.getCommonSettings(language)
            val indentOptions = commonSettings.indentOptions
            indentOptions?.apply {
                INDENT_SIZE = config.tabWidth
                CONTINUATION_INDENT_SIZE = config.tabWidth
                TAB_SIZE = config.tabWidth
                USE_TAB_CHARACTER = config.useTabs
            }

            settings.setRightMargin(language, config.printWidth)
            settings.setSoftMargins(language, mutableListOf(config.printWidth))
        }

        fun commonOxfmtSettingsApplied(config: OxfmtConfig, settings: CodeStyleSettings,
            language: Language): Boolean {
            val commonSettings = settings.getCommonSettings(language)
            val indentOptions = commonSettings.indentOptions

            if (indentOptions == null) {
                return false
            }
            if (indentOptions.INDENT_SIZE != config.tabWidth) {
                return false
            }
            if (indentOptions.CONTINUATION_INDENT_SIZE != config.tabWidth) {
                return false
            }
            if (indentOptions.TAB_SIZE != config.tabWidth) {
                return false
            }
            if (indentOptions.USE_TAB_CHARACTER != config.useTabs) {
                return false
            }
            if (settings.getRightMargin(language) != config.printWidth) {
                return false
            }

            val softMargins = settings.getSoftMargins(language)
            if (softMargins.size != 1) {
                return false
            }
            if (softMargins[0] != config.printWidth) {
                return false
            }

            return true
        }
    }
}
