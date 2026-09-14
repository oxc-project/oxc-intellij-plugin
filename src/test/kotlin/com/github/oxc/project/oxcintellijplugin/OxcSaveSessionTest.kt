package com.github.oxc.project.oxcintellijplugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

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

    fun testCancellationDuringDocumentWriteClosesTheProcess() = checkBlockedDocumentWrite(cancel = true)

    fun testShutdownDuringDocumentWriteClosesTheProcess() = checkBlockedDocumentWrite(cancel = false)

    fun testWriteFailureReleasesTheSaveAndClosesTheProcess() {
        val node = PathEnvironmentVariableUtil.findInPath("node") ?: error("Node is required for LSP tests")
        val file = myFixture.addFileToProject("index.js", "let x = 1;").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val handlerRef = AtomicReference<OSProcessHandler>()
        val writeFailure = java.io.IOException("Test transport failure")
        val descriptor = object : LspServerDescriptor(project, "Save write failure test", file.parent) {
            override fun isSupportedFile(file: VirtualFile) = true
            override fun createCommandLine() = GeneralCommandLine(node.absolutePath, "-e", "setInterval(() => {}, 1000)")
            override fun startServerProcess(): OSProcessHandler = object : OSProcessHandler(createCommandLine()) {
                override fun getProcessInput(): OutputStream = object : OutputStream() {
                    override fun write(value: Int) { throw writeFailure }
                }
            }.also { handlerRef.set(it) }
        }
        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            runBlocking {
                OxcSaveSession(project, OxcLspTool.OXFMT, descriptor).run(file, document) {
                    fail("A failed transport must not run the save action")
                }
            }
        })
        try {
            PlatformTestUtil.waitWithEventsDispatching("Save did not report the write failure", { future.isDone }, 10)
            val failure = runCatching { future.get() }.exceptionOrNull()
            assertTrue("The original write failure was lost", generateSequence(failure) { it.cause }.any { it === writeFailure })
            assertFalse(handlerRef.get().process.isAlive)
            assertTrue(handlerRef.get().isProcessTerminated)
        } finally {
            handlerRef.get()?.let { handler ->
                if (!handler.isStartNotified) handler.startNotify()
                handler.destroyProcess()
                handler.waitFor(5_000)
            }
            PlatformTestUtil.waitWithEventsDispatching("Save did not stop after test cleanup", { future.isDone }, 10)
        }
    }

    private fun checkBlockedDocumentWrite(cancel: Boolean) {
        val node = PathEnvironmentVariableUtil.findInPath("node") ?: error("Node is required for LSP tests")
        val file = myFixture.addFileToProject("index.js", "//" + "x".repeat(2_000_000)).virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val sendingDocument = CountDownLatch(1)
        val handlerRef = AtomicReference<OSProcessHandler>()
        val jobRef = AtomicReference<Job>()
        val script = """
            let input = Buffer.alloc(0);
            process.stdin.on('data', chunk => {
                input = Buffer.concat([input, chunk]);
                const end = input.indexOf('\r\n\r\n');
                if (end < 0) return;
                const size = Number(/Content-Length: (\d+)/i.exec(input.subarray(0, end).toString())[1]);
                if (input.length < end + 4 + size) return;
                const request = JSON.parse(input.subarray(end + 4, end + 4 + size).toString());
                if (request.method !== 'initialize') throw new Error('Expected initialize');
                process.stdin.pause();
                const reply = JSON.stringify({ jsonrpc: '2.0', id: request.id, result: { capabilities: { textDocumentSync: 1 } } });
                process.stdout.write('Content-Length: ' + Buffer.byteLength(reply) + '\r\n\r\n' + reply);
            });
            setInterval(() => {}, 1000);
        """.trimIndent()
        val descriptor = object : LspServerDescriptor(project, "Save write test", file.parent) {
            override fun isSupportedFile(file: VirtualFile) = true
            override fun createCommandLine() = GeneralCommandLine(node.absolutePath, "-e", script)
            override fun startServerProcess(): OSProcessHandler {
                // The IDE uses this handler, whose graceful destroy flushes stdin first.
                return object : KillableProcessHandler(createCommandLine()) {
                    override fun getProcessInput(): OutputStream = object : FilterOutputStream(super.getProcessInput()!!) {
                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            if (length > 1_000_000) sendingDocument.countDown()
                            out.write(bytes, offset, length)
                        }
                    }
                }.also { handlerRef.set(it) }
            }
        }
        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            runBlocking {
                val job = launch {
                    OxcSaveSession(project, OxcLspTool.OXFMT, descriptor).run(file, document) { server ->
                        if (cancel) {
                            server.sendRequest {
                                it.textDocumentService.formatting(DocumentFormattingParams(server.getDocumentIdentifier(file), FormattingOptions(2, true)))
                            }
                        }
                    }
                }
                jobRef.set(job)
                job.join()
            }
        })
        try {
            PlatformTestUtil.waitWithEventsDispatching("Document write did not start", { sendingDocument.count == 0L }, 10)
            if (cancel) jobRef.get().cancel()
            PlatformTestUtil.waitWithEventsDispatching("Save remained blocked during cleanup", { future.isDone }, 10)
            future.get()
            assertEquals(cancel, jobRef.get().isCancelled)
            assertFalse(handlerRef.get().process.isAlive)
            assertTrue(handlerRef.get().isProcessTerminated)
        } finally {
            jobRef.get()?.cancel()
            handlerRef.get()?.let { handler ->
                if (!handler.isStartNotified) handler.startNotify()
                (handler as KillableProcessHandler).killProcess()
                handler.waitFor(5_000)
            }
            PlatformTestUtil.waitWithEventsDispatching("Save did not stop after test cleanup", { future.isDone }, 10)
        }
    }
}
