package com.github.oxc.project.oxcintellijplugin.viteplus

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.util.concurrent.Callable
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

/** Uses real vp entry points; CI installs testData/viteplus/lsp before running the suite. */
class VitePlusLspTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
    override fun setUp() {
        super.setUp()
        myFixture.testDataPath = "src/test/testData/viteplus"
        val node = PathEnvironmentVariableUtil.findInPath("node") ?: error("Node is required for LSP tests")
        NodeJsInterpreterManager.getInstance(project).setInterpreterRef(NodeJsInterpreterRef.create(node.absolutePath), testRootDisposable)
        myFixture.copyDirectoryToProject("lsp", "")
        // Simulate the RFC's removal of Vite+'s standalone wrappers.
        for (tool in listOf("oxlint", "oxfmt")) {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(myFixture.tempDirPath, "node_modules/vite-plus/bin/$tool"))
        }
    }

    fun testViteConfigDiagnosticsFormattingAndIndependentRestart() {
        val file = myFixture.configureByFile("nested/index.js").virtualFile
        val lint = waitForServer(OxlintLspServerSupportProvider::class.java, file)
        val fmt = waitForServer(OxfmtLspServerSupportProvider::class.java, file)

        val diagnostics = request {
            lint.sendRequestSync(15_000) {
                it.textDocumentService.diagnostic(DocumentDiagnosticParams(lint.getDocumentIdentifier(file)))
            }
        }
        assertNotNull(diagnostics)
        assertTrue(diagnostics!!.isRelatedFullDocumentDiagnosticReport)
        assertTrue(diagnostics.relatedFullDocumentDiagnosticReport.items.any { it.code?.left == "eslint(no-debugger)" || it.message.contains("debugger") })

        assertViteFormatting(fmt, file)
        OxfmtServerService.getInstance(project).restartServer()
        PlatformTestUtil.waitWithEventsDispatching("Formatter did not stop", { fmt.state != LspServerState.Running }, 20)
        val restarted = waitForServer(OxfmtLspServerSupportProvider::class.java, file)
        assertNotSame(fmt, restarted)
        assertEquals(LspServerState.Running, lint.state)
        assertViteFormatting(restarted, file)
    }

    fun testInstallationStartsPreviouslyUnavailableServers() {
        val path = java.nio.file.Path.of(myFixture.tempDirPath, "node_modules/vite-plus/bin/vp")
        val vp = VirtualFileManager.getInstance().findFileByNioPath(path)!!
        OxlintSettings.getInstance(project).vitePlusPath = path.toString()
        OxfmtSettings.getInstance(project).vitePlusPath = path.toString()
        WriteCommandAction.runWriteCommandAction(project) { vp.rename(this, "vp.uninstalled") }
        val file = myFixture.configureByFile("index.js").virtualFile
        assertNull(OxlintPackage(project).resolveCommand(file))
        assertNull(OxfmtPackage(project).resolveCommand(file))
        WriteCommandAction.runWriteCommandAction(project) { vp.rename(this, "vp") }
        waitForServer(OxlintLspServerSupportProvider::class.java, file)
        assertViteFormatting(waitForServer(OxfmtLspServerSupportProvider::class.java, file), file)
    }

    private fun assertViteFormatting(server: LspServer, file: VirtualFile) {
        val edits = request {
            server.sendRequestSync(15_000) {
                it.textDocumentService.formatting(DocumentFormattingParams(server.getDocumentIdentifier(file), FormattingOptions(2, true)))
            }
        }
        assertNotNull(edits)
        val document = myFixture.editor.document
        var formatted = document.text
        for (edit in edits!!.sortedByDescending { document.getLineStartOffset(it.range.start.line) + it.range.start.character }) {
            val start = document.getLineStartOffset(edit.range.start.line) + edit.range.start.character
            val end = document.getLineStartOffset(edit.range.end.line) + edit.range.end.character
            formatted = formatted.replaceRange(start, end, edit.newText)
        }
        assertEquals("debugger\nconsole.log('hello')\n", formatted)
    }

    private fun waitForServer(provider: Class<out LspServerSupportProvider>, file: VirtualFile): LspServer {
        val manager = LspServerManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("No running server for ${provider.simpleName}", {
            manager.getServersForProvider(provider).any { it.state == LspServerState.Running && it.descriptor.isSupportedFile(file) }
        }, 30)
        return manager.getServersForProvider(provider).first { it.state == LspServerState.Running && it.descriptor.isSupportedFile(file) }
    }

    private fun <T> request(action: () -> T): T {
        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable { action() })
        PlatformTestUtil.waitWithEventsDispatching("LSP request timed out", { future.isDone }, 20)
        return future.get()
    }
}
