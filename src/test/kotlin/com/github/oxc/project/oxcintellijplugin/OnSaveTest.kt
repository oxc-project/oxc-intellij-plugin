package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import java.nio.file.Files

/**
 * Saving with both "Fix All with Oxlint on Save" and "Fix All with Oxfmt on Save" enabled, as most
 * projects using both tools would. Oxlint's fixes must land first and Oxfmt must format the fixed
 * text. Fixture: `testData/onsave`, where `.oxlintrc.json` turns on `prefer-const`.
 */
class OnSaveTest : OxcLspTestCase() {

    private val fixedAndFormatted = "const x = 1;\nconst a = { b: 1 };\nconsole.log(a);\n"

    override fun setUp() {
        super.setUp()
        setUpFixture("onsave")
        OxlintSettings.getInstance(project).fixAllOnSave = true
        OxfmtSettings.getInstance(project).fixAllOnSave = true
    }

    fun testSavingRightAfterTypingFixesAndFormats() {
        val file = openFile("index.js")
        waitForOxlintServer()
        waitForOxfmtServer()

        // Save before the highlighting daemon has pulled diagnostics for the new text, which is
        // the case the Oxlint service has to warm its code action cache for.
        typeAndSave("let x = 1\n")

        PlatformTestUtil.waitWithEventsDispatching(
            { "Unexpected text after save: ${myFixture.editor.document.text}" },
            { myFixture.editor.document.text == fixedAndFormatted }, 30)
        assertFalse(FileDocumentManager.getInstance().isFileModified(file))
        assertEquals(fixedAndFormatted, Files.readString(file.toNioPath()))
    }

    fun testSavingAfterHighlightingFixesAndFormats() {
        openFile("index.js")
        waitForOxfmtServer()
        waitForOxlintServer()
        typeAndSave("let x = 1\n", waitForHighlighting = true)

        PlatformTestUtil.waitWithEventsDispatching(
            { "Unexpected text after save: ${myFixture.editor.document.text}" },
            { myFixture.editor.document.text == fixedAndFormatted }, 30)
    }

    fun testSavingTwiceIsStable() {
        openFile("index.js")
        waitForOxlintServer()
        waitForOxfmtServer()
        typeAndSave("let x = 1\n")
        PlatformTestUtil.waitWithEventsDispatching("First save did not fix and format",
            { myFixture.editor.document.text == fixedAndFormatted }, 30)

        typeAndSave("")

        assertEquals(fixedAndFormatted, myFixture.editor.document.text)
    }

    fun testOnlyOxlintOnSaveLeavesFormattingAlone() {
        OxfmtSettings.getInstance(project).fixAllOnSave = false
        openFile("index.js")
        waitForOxlintServer()
        typeAndSave("let x = 1\n")

        PlatformTestUtil.waitWithEventsDispatching(
            { "Unexpected text after save: ${myFixture.editor.document.text}" },
            { myFixture.editor.document.text == "const x = 1\nconst a = {b:1}\nconsole.log(a)\n" }, 30)
    }

    /**
     * Inserts [text] at the start of the document (an empty [text] still marks it modified) and
     * runs Save All, the action Ctrl/Cmd+S is bound to; actions on save only run for explicit saves.
     */
    private fun typeAndSave(text: String, waitForHighlighting: Boolean = false) {
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(0, text)
            if (text.isEmpty()) {
                document.insertString(0, " ")
                document.deleteString(0, 1)
            }
        }
        if (waitForHighlighting) {
            waitForOxlintRules("eslint(prefer-const)")
        }
        myFixture.testAction(ActionManager.getInstance().getAction("SaveAll"))
    }
}
