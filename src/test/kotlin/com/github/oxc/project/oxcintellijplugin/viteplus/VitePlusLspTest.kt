package com.github.oxc.project.oxcintellijplugin.viteplus

import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtFixAllOnSaveAction
import com.github.oxc.project.oxcintellijplugin.OxcLspServerPool
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
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import kotlinx.coroutines.runBlocking
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

    fun testNestedStandaloneWorkspaceHasItsOwnServer() {
        myFixture.testDataPath = "src/test/testData/oxlint/highlighting/no-config"
        myFixture.copyDirectoryToProject("node_modules", "node_modules")
        myFixture.addFileToProject("nested-repo/pnpm-workspace.yaml", "packages: []")
        myFixture.addFileToProject("nested-repo/package.json", "{}")
        myFixture.addFileToProject("nested-repo/index.js", "debugger;")
        val outer = myFixture.configureByFile("index.js").virtualFile
        val outerCommand = OxlintPackage(project).resolveCommand(outer)!!
        assertTrue(outerCommand.vitePlus)
        val outerServer = waitForServer(OxlintLspServerSupportProvider::class.java, outer)
        waitForServer(OxfmtLspServerSupportProvider::class.java, outer)
        val inner = myFixture.configureByFile("nested-repo/index.js").virtualFile
        val innerCommand = OxlintPackage(project).resolveCommand(inner)!!
        assertFalse(innerCommand.vitePlus)
        assertNotSame(outerCommand.root, innerCommand.root)
        assertEquals(inner.parent, innerCommand.root)
        assertFalse(outerServer.descriptor.isSupportedFile(inner))
        val innerServer = waitForServer(OxlintLspServerSupportProvider::class.java, inner)
        assertNotSame(outerServer, innerServer)
        myFixture.configureByFile("index.js")
        assertSame(outerServer, waitForServer(OxlintLspServerSupportProvider::class.java, outer))
    }

    fun testSixDeclaringPackagesCanStartBothTools() {
        myFixture.addFileToProject("pnpm-workspace.yaml", "packages: [packages/*]")
        for (i in 1..6) {
            myFixture.addFileToProject("packages/p$i/package.json", """{"devDependencies":{"vite-plus":"*"}}""")
            myFixture.addFileToProject("packages/p$i/index.js", "debugger;\nconsole.log(\"hello\");\n")
            myFixture.addFileToProject("packages/p$i/vite.config.ts", """
                import { defineConfig } from 'vite-plus';
                export default defineConfig({
                    lint: { rules: { 'eslint/no-debugger': 'error' } },
                    fmt: { singleQuote: ${i % 2 == 1}, semi: false },
                });
            """.trimIndent())
        }
        for (i in 1..6) {
            val file = myFixture.configureByFile("packages/p$i/index.js").virtualFile
            waitForServer(OxlintLspServerSupportProvider::class.java, file)
            val fmt = waitForServer(OxfmtLspServerSupportProvider::class.java, file)
            assertViteFormatting(fmt, file, if (i % 2 == 1) "debugger\nconsole.log('hello')\n" else "debugger\nconsole.log(\"hello\")\n")
            assertTrue(oxcServerCount() <= 8)
        }
        // Save an evicted, unselected tab. Its server must be ready before the request is sent.
        val first = myFixture.findFileInTempDir("packages/p1/index.js")
        val firstDocument = FileDocumentManager.getInstance().getDocument(first)!!
        WriteCommandAction.runWriteCommandAction(project) {
            firstDocument.setText("debugger;\nconsole.log(\"unsaved\");\n")
        }
        OxfmtFixAllOnSaveAction().processDocuments(project, arrayOf(firstDocument))
        assertEquals("debugger\nconsole.log('unsaved')\n", firstDocument.text)
        request { runBlocking { OxlintServerService.getInstance(project).fixAll(first, firstDocument) } }
        assertTrue(oxcServerCount() <= 8)

        waitForServer(OxlintLspServerSupportProvider::class.java, first)
        // Selecting an already-open tab must also restart an evicted scope.
        val second = myFixture.findFileInTempDir("packages/p2/index.js")
        FileEditorManager.getInstance(project).openFile(second, true)
        waitForServer(OxlintLspServerSupportProvider::class.java, second)
        waitForServer(OxfmtLspServerSupportProvider::class.java, second)
        assertTrue(oxcServerCount() <= 8)
    }

    fun testRapidScopeChangesAndRestartKeepTheSelectedPackageRunning() {
        myFixture.addFileToProject("pnpm-workspace.yaml", "packages: [packages/*]")
        for (i in 1..6) {
            myFixture.addFileToProject("packages/p$i/package.json", """{"devDependencies":{"vite-plus":"*"}}""")
            myFixture.addFileToProject("packages/p$i/index.js", "debugger;")
        }
        for (i in 1..6) myFixture.configureByFile("packages/p$i/index.js")
        val file = myFixture.file.virtualFile
        // Save immediately, while editor-server starts and restarts are still queued.
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("console.log(\"unsaved\");\n") }
        OxfmtFixAllOnSaveAction().processDocuments(project, arrayOf(document))
        assertEquals("console.log('unsaved')\n", document.text)
        val lint = waitForServer(OxlintLspServerSupportProvider::class.java, file)
        val fmt = waitForServer(OxfmtLspServerSupportProvider::class.java, file)
        OxfmtServerService.getInstance(project).restartServer()
        PlatformTestUtil.waitWithEventsDispatching("Formatter did not stop", { fmt.state != LspServerState.Running }, 20)
        assertNotSame(fmt, waitForServer(OxfmtLspServerSupportProvider::class.java, file))
        assertSame(lint, waitForServer(OxlintLspServerSupportProvider::class.java, file))
        assertTrue(oxcServerCount() <= 8)
    }

    fun testColdLintSaveFixesTheUnsavedDocument() {
        myFixture.addFileToProject("vite.config.ts", """
            import { defineConfig } from 'vite-plus';
            export default defineConfig({ lint: { rules: { 'eslint/no-useless-escape': 'error' } } });
        """.trimIndent())
        val file = myFixture.findFileInTempDir("index.js")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("console.log(\"\\#\");\n") }
        val fixed = request { runBlocking { OxlintServerService.getInstance(project).fixAll(file, document) } }
        assertTrue(fixed)
        assertEquals("console.log(\"#\");\n", document.text)
        waitForServer(OxlintLspServerSupportProvider::class.java, file)
    }

    private fun oxcServerCount(): Int {
        val manager = LspServerManager.getInstance(project)
        return manager.getServersForProvider(OxlintLspServerSupportProvider::class.java).size +
            manager.getServersForProvider(OxfmtLspServerSupportProvider::class.java).size
    }

    private fun assertViteFormatting(server: LspServer, file: VirtualFile, expected: String = "debugger\nconsole.log('hello')\n") {
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
        assertEquals(expected, formatted)
    }

    private fun waitForServer(provider: Class<out LspServerSupportProvider>, file: VirtualFile): LspServer {
        val manager = LspServerManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("No running server for ${provider.simpleName}", {
            OxcLspServerPool.getInstance(project).isIdle &&
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
