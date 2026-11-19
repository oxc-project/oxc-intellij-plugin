package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.intellij.lang.Language
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.lang.typescript.formatter.TypeScriptCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettings

private val JS_SETTINGS_CLASSES = listOf(JSCodeStyleSettings::class.java, TypeScriptCodeStyleSettings::class.java)

private fun defaultLanguages(): List<Language> = listOf(JavascriptLanguage, JavaScriptSupportLoader.TYPESCRIPT)

/** How an Oxfmt config maps to the IDE's code style. Used on both the overlay's copy and the real settings. */
object OxfmtCodeStyle {

  /** Returns false if [settings] already match. [extraLanguage] also gets the indent and margins. */
  fun apply(settings: CodeStyleSettings, config: OxfmtConfig, extraLanguage: Language? = null): Boolean {
    if (isApplied(settings, config, extraLanguage)) return false

    JS_SETTINGS_CLASSES.forEach { settings.getCustomSettings(it).applyConfig(config) }
    languages(extraLanguage).forEach { language ->
      settings.getCommonSettings(language).indentOptions?.apply {
        INDENT_SIZE = config.tabWidth
        CONTINUATION_INDENT_SIZE = config.tabWidth
        TAB_SIZE = config.tabWidth
        USE_TAB_CHARACTER = config.useTabs
      }
      settings.setRightMargin(language, config.printWidth)
      settings.setSoftMargins(language, mutableListOf(config.printWidth))
    }
    return true
  }

  fun isApplied(settings: CodeStyleSettings, config: OxfmtConfig, extraLanguage: Language? = null): Boolean =
    JS_SETTINGS_CLASSES.all { settings.getCustomSettings(it).matches(config) } &&
    languages(extraLanguage).all { language ->
      val indent = settings.getCommonSettings(language).indentOptions
      (indent == null || (
        indent.INDENT_SIZE == config.tabWidth &&
        indent.CONTINUATION_INDENT_SIZE == config.tabWidth &&
        indent.TAB_SIZE == config.tabWidth &&
        indent.USE_TAB_CHARACTER == config.useTabs)) &&
      settings.getRightMargin(language) == config.printWidth &&
      settings.getSoftMargins(language) == listOf(config.printWidth)
    }

  private fun languages(extra: Language?): List<Language> =
    (defaultLanguages() + listOfNotNull(extra)).distinct()

  private fun JSCodeStyleSettings.applyConfig(config: OxfmtConfig) {
    USE_DOUBLE_QUOTES = !config.singleQuote
    USE_SEMICOLON_AFTER_STATEMENT = config.semi
    SPACES_WITHIN_OBJECT_LITERAL_BRACES = config.bracketSpacing
    SPACES_WITHIN_OBJECT_TYPE_BRACES = config.bracketSpacing
    SPACES_WITHIN_IMPORTS = config.bracketSpacing
    ENFORCE_TRAILING_COMMA = config.trailingComma.toIde()
    FORCE_QUOTE_STYlE = true
    FORCE_SEMICOLON_STYLE = true
    SPACE_BEFORE_FUNCTION_LEFT_PARENTH = true
  }

  private fun JSCodeStyleSettings.matches(config: OxfmtConfig): Boolean =
    USE_DOUBLE_QUOTES == !config.singleQuote &&
    USE_SEMICOLON_AFTER_STATEMENT == config.semi &&
    SPACES_WITHIN_OBJECT_LITERAL_BRACES == config.bracketSpacing &&
    SPACES_WITHIN_OBJECT_TYPE_BRACES == config.bracketSpacing &&
    SPACES_WITHIN_IMPORTS == config.bracketSpacing &&
    ENFORCE_TRAILING_COMMA == config.trailingComma.toIde() &&
    FORCE_QUOTE_STYlE &&
    FORCE_SEMICOLON_STYLE &&
    SPACE_BEFORE_FUNCTION_LEFT_PARENTH

  private fun OxfmtTrailingComma.toIde(): JSCodeStyleSettings.TrailingCommaOption = when (this) {
    OxfmtTrailingComma.NONE -> JSCodeStyleSettings.TrailingCommaOption.Remove
    OxfmtTrailingComma.ALL, OxfmtTrailingComma.ES5 -> JSCodeStyleSettings.TrailingCommaOption.WhenMultiline
  }
}
