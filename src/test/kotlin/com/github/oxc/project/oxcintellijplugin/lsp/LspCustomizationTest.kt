package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspFormattingSupport
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspDiagnosticsSupport
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import org.eclipse.lsp4j.DiagnosticCodeDescription
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class LspCustomizationTest : OxcLightTestCase() {

    fun testOxlintMessageIncludesSourceAndRule() {
        val diagnostic = diagnostic(code = "eslint(no-debugger)")

        assertEquals("oxc: `debugger` statement is not allowed eslint(no-debugger)",
            OxlintLspDiagnosticsSupport().getMessage(diagnostic))
    }

    fun testOxlintMessageWithoutRule() {
        assertEquals("oxc: `debugger` statement is not allowed Unknown Code",
            OxlintLspDiagnosticsSupport().getMessage(diagnostic(code = null)))
    }

    fun testOxlintTooltipLinksTheRuleToItsDocumentation() {
        val diagnostic = diagnostic(code = "eslint(no-debugger)").apply {
            codeDescription = DiagnosticCodeDescription("https://oxc.rs/docs/guide/usage/linter/rules/eslint/no-debugger.html")
        }

        val tooltip = OxlintLspDiagnosticsSupport().getTooltip(diagnostic)

        assertTrue(tooltip, tooltip.startsWith("oxc: "))
        assertTrue(tooltip, tooltip.endsWith(
            "<a href=\"https://oxc.rs/docs/guide/usage/linter/rules/eslint/no-debugger.html\">eslint(no-debugger)</a>"))
    }

    fun testOxlintTooltipWithoutDocumentationLink() {
        val tooltip = OxlintLspDiagnosticsSupport().getTooltip(diagnostic(code = "eslint(no-debugger)"))

        assertTrue(tooltip, tooltip.endsWith(" eslint(no-debugger)"))
        assertFalse(tooltip, tooltip.contains("<a "))
    }

    fun testOxfmtFormatsSupportedFilesExclusively() {
        val support = OxfmtLspFormattingSupport(project)

        assertTrue(support.shouldFormatThisFileExclusivelyByServer(file("index.ts"), true, false))
        assertTrue(support.shouldFormatThisFileExclusivelyByServer(file("styles.css"), true, false))
        assertFalse(support.shouldFormatThisFileExclusivelyByServer(file("main.rs"), true, true))
    }

    fun testOxfmtRespectsTheConfiguredExtensions() {
        OxfmtSettings.getInstance(project).supportedExtensions = mutableListOf(".ts")
        val support = OxfmtLspFormattingSupport(project)

        assertTrue(support.shouldFormatThisFileExclusivelyByServer(file("index.ts"), true, false))
        assertFalse(support.shouldFormatThisFileExclusivelyByServer(file("index.js"), true, false))
    }

    private fun diagnostic(code: String?) = Diagnostic(Range(Position(0, 0), Position(0, 8)),
        "`debugger` statement is not allowed", DiagnosticSeverity.Warning, "oxc", code)

    private fun file(name: String) = myFixture.addFileToProject(name, "").virtualFile
}
