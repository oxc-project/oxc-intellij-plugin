package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig.TrailingCommaOption
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class OxfmtConfigTest {

    @Test
    fun defaultsMatchOxfmtDefaults() {
        val default = OxfmtConfig.DEFAULT

        assertEquals(false, default.bracketSameLine)
        assertEquals(true, default.bracketSpacing)
        assertEquals("lf", default.lineSeparator)
        assertEquals(100, default.printWidth)
        assertEquals(true, default.semi)
        assertEquals(false, default.singleQuote)
        assertEquals(2, default.tabWidth)
        assertEquals(TrailingCommaOption.all, default.trailingComma)
        assertEquals(false, default.useTabs)
    }

    @Test
    fun trailingCommaNone_removesTrailingCommas() {
        assertEquals(JSCodeStyleSettings.TrailingCommaOption.Remove,
            OxfmtConfig.convertTrailingCommaOption(TrailingCommaOption.none))
    }

    @Test
    fun trailingCommaAll_addsTrailingCommasWhenMultiline() {
        assertEquals(JSCodeStyleSettings.TrailingCommaOption.WhenMultiline,
            OxfmtConfig.convertTrailingCommaOption(TrailingCommaOption.all))
    }

    /**
     * QUESTIONABLE: `es5` and `all` map to the same IDE option. Oxfmt's `es5` leaves out trailing
     * commas where ES5 does not allow them (e.g. function parameters), so the IDE formatter and
     * Oxfmt disagree on those positions. IntelliJ has no exact equivalent, so this may be the best
     * available approximation, but it is lossy.
     */
    @Test
    fun trailingCommaEs5_isTreatedLikeAll() {
        assertEquals(JSCodeStyleSettings.TrailingCommaOption.WhenMultiline,
            OxfmtConfig.convertTrailingCommaOption(TrailingCommaOption.es5))
    }
}
