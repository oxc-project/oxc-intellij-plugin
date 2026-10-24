package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.PlatformTestUtil

/** Oxlint settings that change which diagnostics are reported, against the real language server. */
class OxlintDiagnosticsTest : OxcLspTestCase() {

    fun testUnusedDisableDirectivesAreAllowedByDefault() {
        setUpFixture("oxlint/unused-directives")
        openFile("index.js")
        waitForOxlintServer()

        assertNoOxlintHighlightsAfterServerResponds()
    }

    fun testUnusedDisableDirectivesAsWarnings() {
        setUpFixture("oxlint/unused-directives")
        OxlintSettings.getInstance(project).unusedDisableDirectivesSeverity = OxlintUnusedDisableDirectivesSeverity.WARN
        openFile("index.js")
        waitForOxlintServer()

        val highlight = waitForOxlintHighlights { it.any { h -> h.description.contains("Unused oxlint-disable directive") } }
            .first { it.description.contains("Unused oxlint-disable directive") }

        assertEquals(HighlightSeverity.WARNING, highlight.severity)
    }

    fun testUnusedDisableDirectivesAsErrors() {
        setUpFixture("oxlint/unused-directives")
        OxlintSettings.getInstance(project).unusedDisableDirectivesSeverity = OxlintUnusedDisableDirectivesSeverity.DENY
        openFile("index.js")
        waitForOxlintServer()

        val highlight = waitForOxlintHighlights { it.any { h -> h.description.contains("Unused oxlint-disable directive") } }
            .first { it.description.contains("Unused oxlint-disable directive") }

        assertEquals(HighlightSeverity.ERROR, highlight.severity)
    }

    fun testTypeAwareRulesRunWhenEnabled() {
        setUpFixture("oxlint/type-aware")
        OxlintSettings.getInstance(project).typeAware = true
        openFile("index.ts")
        waitForOxlintServer()

        waitForOxlintRules("eslint(no-debugger)", "no-floating-promises")
    }

    fun testTypeAwareRulesDoNotRunWhenDisabled() {
        setUpFixture("oxlint/type-aware")
        openFile("index.ts")
        waitForOxlintServer()

        val highlights = waitForOxlintRules("eslint(no-debugger)")

        assertFalse(highlights.any { it.description.contains("no-floating-promises") })
    }

    fun testVueSvelteAndAstroFilesAreLinted() {
        setUpFixture("oxlint/frameworks")
        for (file in listOf("App.vue", "App.svelte", "Page.astro")) {
            openFile(file)
            waitForOxlintServer()
            waitForOxlintRules("eslint(no-debugger)")
        }
    }

    fun testUnsupportedFilesDoNotStartTheServer() {
        setUpFixture("oxlint/frameworks")
        openFile("notes.md")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEmpty(servers(OxlintLspServerSupportProvider::class.java))
    }

    fun testCustomExtensionsAreLinted() {
        setUpFixture("oxlint/frameworks")
        OxlintSettings.getInstance(project).supportedExtensions = mutableListOf(".vue")

        openFile("App.svelte")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEmpty(servers(OxlintLspServerSupportProvider::class.java))

        openFile("App.vue")
        waitForOxlintServer()
        waitForOxlintRules("eslint(no-debugger)")
    }

    fun testDisabledModeDoesNotStartTheServer() {
        setUpFixture("oxlint/unused-directives")
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED

        openFile("index.js")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEmpty(servers(OxlintLspServerSupportProvider::class.java))
    }

    fun testManualBinaryPathIsUsed() {
        setUpFixture("oxlint/unused-directives")
        OxlintSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            binaryPath = projectDir.resolve("node_modules/oxlint/bin/oxlint").toString()
        }
        writeProjectFile("index.js", "debugger;\n")

        openFile("index.js")
        waitForOxlintServer()

        waitForOxlintRules("eslint(no-debugger)")
    }

    /**
     * QUESTIONABLE: in MANUAL mode with no config path, an empty string is sent as `configPath`.
     * The server treats that like "no explicit config", so `.oxlintrc.json` files are still found;
     * sending `null` (as AUTOMATIC mode does) would be the clearer contract.
     */
    fun testManualModeWithoutConfigPathStillUsesTheProjectConfig() {
        setUpFixture("oxlint/unused-directives")
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.MANUAL
        writeProjectFile("index.js", "debugger;\n")

        openFile("index.js")
        waitForOxlintServer()

        val highlight = waitForOxlintRules("eslint(no-debugger)").first { it.description.contains("no-debugger") }
        // The project config raises no-debugger to "error".
        assertEquals(HighlightSeverity.ERROR, highlight.severity)
    }

    /** Waits for the server to answer a request for this file, then checks there is nothing to show. */
    private fun assertNoOxlintHighlightsAfterServerResponds() {
        // Add a violation that is always reported, wait for it, then remove it again.
        val document = myFixture.editor.document
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(document.textLength, "debugger;\n")
        }
        waitForOxlintRules("eslint(no-debugger)")
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.deleteString(document.textLength - "debugger;\n".length, document.textLength)
        }
        waitForOxlintHighlights { it.isEmpty() }
    }
}
