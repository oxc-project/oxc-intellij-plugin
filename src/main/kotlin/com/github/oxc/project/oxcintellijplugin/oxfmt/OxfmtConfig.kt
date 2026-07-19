package com.github.oxc.project.oxcintellijplugin.oxfmt

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
    val lineSeparator: String, val printWidth: Int, val semi: Boolean, val singleQuote: Boolean,
    val tabWidth: Int, val trailingComma: TrailingCommaOption, val useTabs: Boolean) {

    enum class TrailingCommaOption { none, all, es5
    }

    companion object {

        val DEFAULT = OxfmtConfig(BRACKET_SAME_LINE_DEFAULT, BRACKET_SPACING_DEFAULT,
            END_OF_LINE_DEFAULT, PRINT_WIDTH_DEFAULT, SEMI_DEFAULT, SINGLE_QUOTE_DEFAULT,
            TAB_WIDTH_DEFAULT, TrailingCommaOption.valueOf(TRAILING_COMMA_DEFAULT),
            USE_TABS_DEFAULT)

        fun convertTrailingCommaOption(
            trailingComma: TrailingCommaOption): JSCodeStyleSettings.TrailingCommaOption {
            return when (trailingComma) {
                TrailingCommaOption.none -> JSCodeStyleSettings.TrailingCommaOption.Remove
                TrailingCommaOption.es5,
                TrailingCommaOption.all,
                    -> JSCodeStyleSettings.TrailingCommaOption.WhenMultiline
            }
        }
    }

}
