package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.util.concurrent.Callable
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

@TestDataPath("\$CONTENT_ROOT/testData/oxfmt/formatting")
class NestedConfigFormattingTest :
    CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        myFixture.testDataPath = "src/test/testData/oxfmt/formatting"
        myFixture.copyDirectoryToProject("nested-config", "")
    }

    fun testRootFileUsesRootConfig() {
        assertFormatted("index.js", "const greeting = 'Happy developing ✨';\n")
    }

    fun testSubdirectoryFileUsesNestedConfig() {
        assertFormatted("subdirectory/index.js", "const greeting = \"Happy developing ✨\"\n")
    }

    fun testSubdirectoryFileUsesRootConfigWhenNestedConfigDisabled() {
        OxfmtSettings.getInstance(project).disableNestedConfig = true

        assertFormatted("subdirectory/index.js", "const greeting = 'Happy developing ✨';\n")
    }

    private fun assertFormatted(filePath: String, expected: String) {
        val file = myFixture.configureByFile(filePath).virtualFile
        val server = waitForServer()

        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            server.sendRequestSync(15_000) {
                it.textDocumentService.formatting(
                    DocumentFormattingParams(server.getDocumentIdentifier(file),
                        FormattingOptions(2, true)))
            }
        })
        PlatformTestUtil.waitWithEventsDispatching("Formatting request timed out",
            { future.isDone }, 20)
        // The server responds with null when the file is already formatted.
        val edits = future.get().orEmpty()

        val document = myFixture.editor.document
        var formatted = document.text
        for (edit in edits.sortedByDescending {
            document.getLineStartOffset(it.range.start.line) + it.range.start.character
        }) {
            val start = document.getLineStartOffset(edit.range.start.line) + edit.range.start.character
            val end = document.getLineStartOffset(edit.range.end.line) + edit.range.end.character
            formatted = formatted.replaceRange(start, end, edit.newText)
        }
        assertEquals(expected, formatted)
    }

    private fun waitForServer(): LspServer {
        val manager = LspServerManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("No running Oxfmt server", {
            manager.getServersForProvider(OxfmtLspServerSupportProvider::class.java)
                .any { it.state == LspServerState.Running }
        }, 30)
        return manager.getServersForProvider(OxfmtLspServerSupportProvider::class.java)
            .first { it.state == LspServerState.Running }
    }
}
