package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle.OxfmtCodeStyleInstaller.Companion.applyCommonOxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle.OxfmtCodeStyleInstaller.Companion.commonOxfmtSettingsApplied
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.lang.typescript.formatter.TypeScriptCodeStyleSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings

class JsOxfmtCodeStyleInstaller : OxfmtCodeStyleInstaller {

    private val javascriptConfigurator = JsOxfmtCodeStyleConfigurator(JavascriptLanguage,
        JSCodeStyleSettings::class)
    private val typescriptConfigurator = JsOxfmtCodeStyleConfigurator(
        JavaScriptSupportLoader.TYPESCRIPT,
        TypeScriptCodeStyleSettings::class)

    override fun install(project: Project, config: OxfmtConfig, settings: CodeStyleSettings) {
        javascriptConfigurator.applySettings(settings, config)
        applyCommonOxfmtSettings(config, settings, JavascriptLanguage)

        typescriptConfigurator.applySettings(settings, config)
        applyCommonOxfmtSettings(config, settings, JavaScriptSupportLoader.TYPESCRIPT)
    }

    override fun isInstalled(project: Project, config: OxfmtConfig,
        settings: CodeStyleSettings): Boolean {
        if (!javascriptConfigurator.isAlreadyApplied(settings, config)) {
            return false
        }
        if (!commonOxfmtSettingsApplied(config, settings, JavascriptLanguage)) {
            return false
        }

        if (!typescriptConfigurator.isAlreadyApplied(settings, config)) {
            return false
        }
        if (!commonOxfmtSettingsApplied(config, settings, JavaScriptSupportLoader.TYPESCRIPT)) {
            return false
        }

        return true
    }
}
