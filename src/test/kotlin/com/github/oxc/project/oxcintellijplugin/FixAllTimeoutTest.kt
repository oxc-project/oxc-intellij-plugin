package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil

/**
 * "Fix All" against a language server that never answers, using the fake server (see
 * `testData/fake-server`). Oxlint's per-file budget comes from the `oxc.lint.fix.all.timeout.ms`
 * registry key; Oxfmt's is fixed at 5 seconds.
 */
class FixAllTimeoutTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        Registry.get("oxc.lint.fix.all.timeout.ms").setValue(500, testRootDisposable)
    }

    fun testOxlintFixAllActionTimesOutAndNotifies() {
        setUpFakeServer("""{ "hang": ["textDocument/codeAction"] }""", oxfmt = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxlintServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        assertEquals(listOf("Oxlint 'Fix All' timed out"), notifications.map { it.title })
        assertEquals(NotificationType.WARNING, notifications.single().type)
        assertEquals("let a = {b:1}\n", myFixture.editor.document.text)
    }

    fun testOxlintFixAllOnSaveSkipsTheFileWhenTheServerTimesOut() {
        setUpFakeServer("""{ "hang": ["textDocument/codeAction"] }""", oxfmt = false)
        OxlintSettings.getInstance(project).fixAllOnSave = true
        val notifications = collectNotifications()
        val file = openFile("index.js")
        waitForOxlintServer()

        typeAndSave("let x = 1\n")

        PlatformTestUtil.waitWithEventsDispatching("The file was not saved", {
            !FileDocumentManager.getInstance().isFileModified(file)
        }, 30)
        assertEquals("let x = 1\nlet a = {b:1}\n", myFixture.editor.document.text)
        assertEmpty(notifications)
    }

    /**
     * QUESTIONABLE: Oxfmt's "Fix All" has a fixed 5 second budget that cannot be configured,
     * unlike Oxlint's (registry key `oxc.lint.fix.all.timeout.ms`), and a timeout is reported as a
     * generic failure with the coroutine's message rather than as a timeout.
     */
    fun testOxfmtFixAllActionTimesOutAfterFiveSecondsAndReportsAFailure() {
        setUpFakeServer("""{ "hang": ["textDocument/formatting"] }""", oxlint = false)
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxfmtServer()

        val start = System.currentTimeMillis()
        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtApplyFixAllAction"))

        assertTrue(System.currentTimeMillis() - start >= 5_000)
        assertEquals(listOf("Failed to Apply 'Fix All'"), notifications.map { it.title })
        assertEquals(NotificationType.ERROR, notifications.single().type)
        assertTrue(notifications.single().content, notifications.single().content.contains("Timed out"))
    }

    /**
     * QUESTIONABLE: Oxfmt's on-save budget (5 seconds) covers all documents of one save together
     * rather than each file, and a timeout shows an error notification, while Oxlint skips the
     * file and only logs a warning.
     */
    fun testOxfmtFixAllOnSaveTimesOutAndReportsAFailure() {
        setUpFakeServer("""{ "hang": ["textDocument/formatting"] }""", oxlint = false)
        OxfmtSettings.getInstance(project).fixAllOnSave = true
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxfmtServer()

        typeAndSave("let x = 1\n")

        PlatformTestUtil.waitWithEventsDispatching("No failure was reported", { notifications.isNotEmpty() }, 30)
        assertEquals(listOf("Failed to apply 'Fix all' on save"), notifications.map { it.title })
        assertEquals("let x = 1\nlet a = {b:1}\n", myFixture.editor.document.text)
    }

    private fun typeAndSave(text: String) {
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, text) }
        myFixture.testAction(ActionManager.getInstance().getAction("SaveAll"))
    }
}
