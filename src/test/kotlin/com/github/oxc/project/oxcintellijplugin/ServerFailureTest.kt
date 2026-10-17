package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.notification.NotificationType
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil

/**
 * Language servers that are missing, crash or fail requests, using the fake server (see
 * `testData/fake-server`). The IDE must stay usable, report failures, and recover once the
 * server works again.
 */
class ServerFailureTest : OxcLspTestCase() {

    fun testServerThatCrashesOnStartupDoesNotRun() {
        setUpFakeServer("""{ "crashOn": "initialized" }""", oxfmt = false)

        openFile("index.js")

        waitForServerToStop()
    }

    fun testRestartActionRecoversOnceTheServerWorksAgain() {
        setUpFakeServer("""{ "crashOn": "initialized" }""", oxfmt = false)
        openFile("index.js")
        waitForServerToStop()

        setFakeServerBehavior("{}")
        myFixture.testAction(ActionManager.getInstance().getAction("OxlintRestartServerAction"))

        waitForOxlintServer()
    }

    /**
     * QUESTIONABLE: when the server crashes while computing "Fix All", the pending request is not
     * failed, so the action keeps its modal progress open until the per-file timeout (30 seconds
     * by default) and then reports a timeout instead of the crash.
     */
    fun testServerCrashingDuringFixAllIsReportedAsATimeout() {
        Registry.get("oxc.lint.fix.all.timeout.ms").setValue(2_000, testRootDisposable)
        setUpFakeServer("""{ "crashOn": "textDocument/codeAction" }""", oxfmt = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxlintServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        waitForServerToStop()
        assertEquals(listOf("Oxlint 'Fix All' timed out"), notifications.map { it.title })
        assertEquals("let a = {b:1}\n", myFixture.editor.document.text)
    }

    fun testMissingManualBinaryDoesNotStartAServer() {
        setUpFakeServer(oxfmt = false)
        OxlintSettings.getInstance(project).binaryPath = projectDir.resolve("missing/oxlint").toString()

        openFile("index.js")

        waitForServerToStop()
    }

    /**
     * QUESTIONABLE: a binary that is a Node.js script needs a Node.js interpreter. Without one, the
     * server descriptor throws while the file is opened, which the IDE reports as an internal
     * error ("Local or WSL Node.js runtime not configured.") instead of telling the user what to
     * configure.
     */
    fun testNodeScriptWithoutANodeInterpreterIsReportedAsAnInternalError() {
        setUpFakeServer(oxfmt = false)
        NodeJsInterpreterManager.getInstance(project).setInterpreterRef(NodeJsInterpreterRef.create(""), testRootDisposable)
        val errors = mutableListOf<String>()

        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>,
                t: Throwable?): Set<Action> {
                errors.add("$message ${t?.message}")
                return emptySet()
            }
        }) {
            openFile("index.js")
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        assertEmpty(servers(OxlintLspServerSupportProvider::class.java))
        assertTrue(errors.toString(), errors.any { it.contains("Node.js runtime not configured") })
    }

    fun testOxlintFixAllReportsAServerError() {
        setUpFakeServer("""{ "fail": ["textDocument/codeAction"] }""", oxfmt = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxlintServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        assertEquals(listOf("Failed to Apply 'Fix All'"), notifications.map { it.title })
        assertEquals(NotificationType.ERROR, notifications.single().type)
        assertTrue(notifications.single().content, notifications.single().content.contains("fake failure"))
    }

    fun testOxfmtFixAllReportsAServerError() {
        setUpFakeServer("""{ "fail": ["textDocument/formatting"] }""", oxlint = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxfmtServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtApplyFixAllAction"))

        assertEquals(listOf("Failed to Apply 'Fix All'"), notifications.map { it.title })
        assertEquals(NotificationType.ERROR, notifications.single().type)
    }

    /**
     * QUESTIONABLE: when no Oxlint server is running (here it crashes on startup), "Fix All" silently
     * does nothing; the user gets no hint that the server is down.
     */
    fun testOxlintFixAllWithoutARunningServerIsSilent() {
        setUpFakeServer("""{ "crashOn": "initialized" }""", oxfmt = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForServerToStop()

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        assertEmpty(notifications)
    }

    /**
     * QUESTIONABLE: when no Oxfmt server is running (here it crashes on startup), "Fix All" reports
     * success although nothing was formatted.
     */
    fun testOxfmtFixAllWithoutARunningServerReportsSuccess() {
        setUpFakeServer("""{ "crashOn": "initialized" }""", oxlint = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForServerToStop(OxfmtLspServerSupportProvider::class.java)

        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtApplyFixAllAction"))

        assertEquals(listOf("Fixes applied successfully"), notifications.map { it.title })
        assertEquals("let a = {b:1}\n", myFixture.editor.document.text)
    }

    fun testShutdownIsSentWhenTheServerIsStopped() {
        setUpFakeServer(oxfmt = false)
        val file = openFile("index.js")
        waitForOxlintServer()

        FileEditorManager.getInstance(project).closeFile(file)
        waitForNoRunningServer(OxlintLspServerSupportProvider::class.java)

        PlatformTestUtil.waitWithEventsDispatching({ "Fake server log: ${fakeServerLog()}" },
            { fakeServerLog().containsAll(listOf("shutdown", "exit")) }, 30)
    }

    /** Waits until the server has been started and died, which the platform reports as a crash. */
    private fun waitForServerToStop(
        provider: Class<out LspServerSupportProvider> = OxlintLspServerSupportProvider::class.java) {
        PlatformTestUtil.waitWithEventsDispatching({ "Servers: ${servers(provider).map { it.state }}" }, {
            servers(provider).map { it.state } == listOf(LspServerState.ShutdownUnexpectedly)
        }, 30)
    }
}
