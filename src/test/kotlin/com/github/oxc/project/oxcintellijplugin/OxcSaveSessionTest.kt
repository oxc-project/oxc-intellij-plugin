package com.github.oxc.project.oxcintellijplugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class OxcSaveSessionTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
    fun testCancellationDuringStartupClosesTheProcess() {
        val node = PathEnvironmentVariableUtil.findInPath("node") ?: error("Node is required for LSP tests")
        val file = myFixture.addFileToProject("index.js", "let x = 1;").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val created = CompletableDeferred<OSProcessHandler>()
        val release = CountDownLatch(1)
        val descriptor = object : LspServerDescriptor(project, "Save cancellation test", file.parent) {
            override fun isSupportedFile(file: VirtualFile) = true
            override fun createCommandLine() = GeneralCommandLine(node.absolutePath, "-e", "setInterval(() => {}, 1000)")
            override fun startServerProcess(): OSProcessHandler {
                val handler = OSProcessHandler(createCommandLine())
                created.complete(handler)
                check(release.await(10, TimeUnit.SECONDS))
                return handler
            }
        }
        runBlocking {
            val job = launch {
                OxcSaveSession(project, OxcLspTool.OXFMT, descriptor).run(file, document) {
                    fail("A cancelled save must not run its action")
                }
            }
            val handler = withTimeout(10_000) { created.await() }
            try {
                // Cancel after process creation but before the IO dispatcher returns its handler.
                job.cancel()
                release.countDown()
                withTimeout(10_000) { job.join() }
                assertTrue(job.isCancelled)
                assertFalse("Cancelled save left its child process running", handler.process.isAlive)
                assertTrue(handler.isProcessTerminated)
            } finally {
                release.countDown()
                job.cancel()
                if (!handler.isStartNotified) handler.startNotify()
                handler.destroyProcess()
                handler.waitFor(5_000)
            }
        }
    }
}
