package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtFixAllOnSaveAction
import com.github.oxc.project.oxcintellijplugin.oxlint.actions.OxlintFixAllOnSaveAction
import com.intellij.codeInsight.actions.onSave.OptimizeImportsOnSaveOptions
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.readText
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiDocumentManager
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

    fun testOxfmtUsesDocumentUpdatingSavePhaseAfterIdeFormattingAndOxlint() {
        val actions = ExtensionPointName.create<ActionsOnSaveFileDocumentManagerListener.ActionOnSave>(
            "com.intellij.actionOnSave").extensionList
        val oxfmt = actions.single { it is OxfmtFixAllOnSaveAction }
        assertTrue(oxfmt is ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave)
        val oxfmtIndex = actions.indexOf(oxfmt)
        assertTrue(actions.indexOfFirst { it.javaClass.simpleName == "FormatOnSaveAction" } in 0 until oxfmtIndex)
        assertTrue(actions.indexOfFirst { it is OxlintFixAllOnSaveAction } in 0 until oxfmtIndex)
    }

    fun testSaveFormatsAfterOptimizingImportsAndWritesResultToDisk() {
        myFixture.addFileToProject("exports.js", "export const used = 1;\nexport const unused = 2;\n")
        val file = myFixture.configureByText("imports.js", "console.log('initial');\n").virtualFile
        waitForServer()

        val options = OptimizeImportsOnSaveOptions.getInstance(project)
        options.isRunOnSaveEnabled = true
        assertTrue(options.isFileTypeSelected(myFixture.file.fileType))
        OxfmtSettings.getInstance(project).fixAllOnSave = true

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(
                "import {unused,used} from \"./exports\";\nconsole.log(used);\n")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()

        val saveManager = ActionsOnSaveManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("Save actions timed out",
            { !saveManager.hasPendingActions() }, 30)

        val expected = "import { used } from './exports';\n\nconsole.log(used);\n"
        assertEquals(expected, myFixture.editor.document.text)
        assertEquals(expected, file.readText())
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(myFixture.editor.document))
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
