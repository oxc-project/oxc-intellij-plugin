package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig.Companion.convertTrailingCommaOption
import com.intellij.lang.Language
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettings
import kotlin.reflect.KClass

class JsOxfmtCodeStyleConfigurator(
    private val language: Language,
    private val customSettingsClass: KClass<out JSCodeStyleSettings>) : OxfmtCodeStyleConfigurator {

    override fun applySettings(settings: CodeStyleSettings, config: OxfmtConfig) {
        settings.getCustomSettings(customSettingsClass.java).apply {
            USE_DOUBLE_QUOTES = !config.singleQuote
            USE_SEMICOLON_AFTER_STATEMENT = config.semi
            SPACES_WITHIN_OBJECT_LITERAL_BRACES = config.bracketSpacing
            SPACES_WITHIN_OBJECT_TYPE_BRACES = config.bracketSpacing
            SPACES_WITHIN_IMPORTS = config.bracketSpacing
            ENFORCE_TRAILING_COMMA = convertTrailingCommaOption(config.trailingComma)

            FORCE_QUOTE_STYlE = true
            FORCE_SEMICOLON_STYLE = true
            SPACE_BEFORE_FUNCTION_LEFT_PARENTH = true
        }
    }

    override fun isAlreadyApplied(settings: CodeStyleSettings, config: OxfmtConfig): Boolean {
        val customSettings = settings.getCustomSettings(customSettingsClass.java)

        if (customSettings.USE_DOUBLE_QUOTES == config.singleQuote) {
            return false
        }
        if (customSettings.USE_SEMICOLON_AFTER_STATEMENT != config.semi) {
            return false
        }
        if (customSettings.SPACES_WITHIN_OBJECT_LITERAL_BRACES != config.bracketSpacing) {
            return false
        }
        if (customSettings.SPACES_WITHIN_OBJECT_TYPE_BRACES != config.bracketSpacing) {
            return false
        }
        if (customSettings.SPACES_WITHIN_IMPORTS != config.bracketSpacing) {
            return false
        }
        if (customSettings.ENFORCE_TRAILING_COMMA != convertTrailingCommaOption(
                config.trailingComma)
        ) {
            return false
        }
        if (!customSettings.FORCE_QUOTE_STYlE) {
            return false
        }
        if (!customSettings.FORCE_SEMICOLON_STYLE) {
            return false
        }
        if (!customSettings.SPACE_BEFORE_FUNCTION_LEFT_PARENTH) {
            return false
        }

        return true
    }
}
