package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.google.gson.JsonObject
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings

private const val BRACKET_SAME_LINE_DEFAULT = false
private const val BRACKET_SPACING_DEFAULT = true
private const val END_OF_LINE_DEFAULT = "lf"
private const val PRINT_WIDTH_DEFAULT = 100
private const val SEMI_DEFAULT = true
private const val SINGLE_QUOTE_DEFAULT = false
private const val TAB_WIDTH_DEFAULT = 2
private const val TRAILING_COMMA_DEFAULT = "all"
private const val USE_TABS_DEFAULT = false

data class OxfmtConfig(val bracketSameLine: Boolean, val bracketSpacing: Boolean,
    val lineSeparator: String, val printWidth: Int, val semi: Boolean,
    val singleQuote: Boolean, val tabWidth: Int, val trailingComma: TrailingCommaOption,
    val useTabs: Boolean) {

    enum class TrailingCommaOption { none, all, es5
    }

    companion object {

        fun convertTrailingCommaOption(
            trailingComma: TrailingCommaOption): JSCodeStyleSettings.TrailingCommaOption {
            return when (trailingComma) {
                TrailingCommaOption.none -> JSCodeStyleSettings.TrailingCommaOption.Remove
                TrailingCommaOption.es5,
                TrailingCommaOption.all,
                    -> JSCodeStyleSettings.TrailingCommaOption.WhenMultiline
            }
        }

        fun fromGsonJsonObject(json: JsonObject): OxfmtConfig {
            return OxfmtConfig(json.get("bracketSameLine")?.asBoolean ?: BRACKET_SAME_LINE_DEFAULT,
                json.get("bracketSpacing")?.asBoolean ?: BRACKET_SPACING_DEFAULT,
                json.get("endOfLine")?.asString ?: END_OF_LINE_DEFAULT,
                json.get("printWidth")?.asInt ?: PRINT_WIDTH_DEFAULT,
                json.get("semi")?.asBoolean ?: SEMI_DEFAULT,
                json.get("singleQuote")?.asBoolean ?: SINGLE_QUOTE_DEFAULT,
                json.get("tabWidth")?.asInt ?: TAB_WIDTH_DEFAULT, TrailingCommaOption.valueOf(
                    json.get("trailingComma")?.asString ?: TRAILING_COMMA_DEFAULT),
                json.get("useTabs")?.asBoolean ?: USE_TABS_DEFAULT)
        }
    }

}
