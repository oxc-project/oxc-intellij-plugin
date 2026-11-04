package com.github.oxc.project.oxcintellijplugin.oxlint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OxlintEnumsTest {

    @Test
    fun fixKind_lspValuesMatchTheLanguageServerOptions() {
        assertEquals(
            mapOf(
                OxlintFixKind.SAFE_FIX to "safe_fix",
                OxlintFixKind.SAFE_FIX_OR_SUGGESTION to "safe_fix_or_suggestion",
                OxlintFixKind.DANGEROUS_FIX to "dangerous_fix",
                OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION to "dangerous_fix_or_suggestion",
                OxlintFixKind.NONE to "none",
                OxlintFixKind.ALL to "all",
            ),
            OxlintFixKind.entries.associateWith { it.toLspValue() })
    }

    @Test
    fun fixKind_dangerousKinds() {
        assertTrue(OxlintFixKind.DANGEROUS_FIX.isDangerous())
        assertTrue(OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION.isDangerous())
        assertTrue(OxlintFixKind.ALL.isDangerous())
        assertFalse(OxlintFixKind.SAFE_FIX.isDangerous())
        assertFalse(OxlintFixKind.SAFE_FIX_OR_SUGGESTION.isDangerous())
        assertFalse(OxlintFixKind.NONE.isDangerous())
    }

    @Test
    fun fixKind_suggestionKinds() {
        assertTrue(OxlintFixKind.SAFE_FIX_OR_SUGGESTION.isSuggestion())
        assertTrue(OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION.isSuggestion())
        assertTrue(OxlintFixKind.ALL.isSuggestion())
        assertFalse(OxlintFixKind.SAFE_FIX.isSuggestion())
        assertFalse(OxlintFixKind.DANGEROUS_FIX.isSuggestion())
        assertFalse(OxlintFixKind.NONE.isSuggestion())
    }

    @Test
    fun unusedDisableDirectives_lspValuesMatchTheLanguageServerOptions() {
        assertEquals(
            mapOf(
                OxlintUnusedDisableDirectivesSeverity.ALLOW to "allow",
                OxlintUnusedDisableDirectivesSeverity.WARN to "warn",
                OxlintUnusedDisableDirectivesSeverity.DENY to "deny",
            ),
            OxlintUnusedDisableDirectivesSeverity.entries.associateWith { it.toLspValue() })
    }
}
