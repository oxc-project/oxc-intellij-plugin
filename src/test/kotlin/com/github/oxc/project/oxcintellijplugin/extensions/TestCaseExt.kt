package com.github.oxc.project.oxcintellijplugin.extensions

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

/**
 * Opens [filePath], whose text contains `<warning>`/`<error>` markup, and checks that the language
 * server's highlighting matches it. Oxlint uses pull diagnostics, which arrive asynchronously after
 * highlighting starts, so the check is retried until it passes or [timeoutSeconds] elapse.
 */
fun CodeInsightTestFixture.configureByFileAndCheckLanguageServerHighlighting(filePath: String,
    timeoutSeconds: Int = 60) {
    configureByFile(filePath)
    // init() strips the markup from the document, so it happens once; the check is repeated.
    val expected = ExpectedHighlightingData(editor.document, true, true, true, false)
    expected.init()
    val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
    while (true) {
        try {
            (this as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(expected)
            return
        } catch (e: AssertionError) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("${e.message}\n${project.lspServerStates()}", e)
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(250)
        }
    }
}

/**
 * The state of each Oxlint and Oxfmt server, for failure messages: tells whether diagnostics were
 * missing because no server was running.
 */
fun Project.lspServerStates(): String {
    val manager = LspServerManager.getInstance(this)
    fun states(provider: Class<out LspServerSupportProvider>) =
        manager.getServersForProvider(provider).map { it.state }
    return "Oxlint servers: ${states(OxlintLspServerSupportProvider::class.java)}, " +
        "Oxfmt servers: ${states(OxfmtLspServerSupportProvider::class.java)}."
}
