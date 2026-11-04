package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.PlatformTestUtil

/**
 * The oldest Oxlint the plugin supports (1.29.0, see CHANGELOG 0.0.44) must still lint, offer
 * quick fixes and apply "Fix All". The rest of the suite runs the latest versions. No minimum
 * Oxfmt version is documented, so Oxfmt is not covered here. Fixture: `testData/minimum-versions`.
 *
 * Differences from current versions that the expectations reflect: 1.29.0 has no `prefer-const`,
 * and removing `debugger` is a safe fix there rather than a suggestion.
 */
class MinimumVersionsTest : OxcLspTestCase() {

    private val fixed = "let b = 2;\n\nif (b == 2) {console.log(b);}\n"

    override fun setUp() {
        super.setUp()
        setUpFixture("minimum-versions")
    }

    fun testDiagnosticsAreReported() {
        openFile("index.js")
        waitForOxlintServer()

        waitForOxlintRules("eslint(curly)", "eslint(eqeqeq)", "eslint(no-debugger)")
    }

    fun testQuickFixesAreOffered() {
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(curly)")

        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("console.log"))
        val intentions = myFixture.availableIntentions.map(IntentionAction::getText)
        assertTrue(intentions.toString(), intentions.any { it.contains("{console.log(b);}") })
    }

    fun testFixAllAction() {
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(curly)")

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        assertEquals(fixed, myFixture.editor.document.text)
    }

    fun testFixAllOnSave() {
        OxlintSettings.getInstance(project).fixAllOnSave = true
        openFile("index.js")
        waitForOxlintServer()
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "let x = 1;\n") }

        myFixture.testAction(ActionManager.getInstance().getAction("SaveAll"))

        PlatformTestUtil.waitWithEventsDispatching({ "Unexpected text after save: ${document.text}" },
            { document.text == "let x = 1;\n$fixed" }, 30)
    }

    fun testChangingTheConfigRestartsTheServerWithTheNewRules() {
        openFile("index.js")
        val before = waitForOxlintServer()

        writeProjectFile(".oxlintrc.json", """{ "rules": { "no-console": "error" } }""")

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxlintServer() !== before
        }, 30)
        waitForOxlintRules("eslint(no-console)")
    }
}
