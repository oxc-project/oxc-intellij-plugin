package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxlint.actions.OxlintFixAllOnSaveAction
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.PlatformTestUtil

/**
 * Oxlint "Fix All" (the action, the on-save action and the service behind them) against the real
 * language server. The fixture (`testData/oxlint/fixes`) has one violation of each fix category:
 * `prefer-const`/`curly` (safe fix), `no-debugger` (suggestion) and `eqeqeq` (dangerous fix).
 */
class OxlintFixesTest : OxcLspTestCase() {

    private val original = "let b = 2;\ndebugger;\nif (b == 2) console.log(b);\n"

    override fun setUp() {
        super.setUp()
        setUpFixture("oxlint/fixes")
    }

    fun testSafeFix() {
        assertFixAll(OxlintFixKind.SAFE_FIX, "const b = 2;\ndebugger;\nif (b == 2) {console.log(b);}\n")
    }

    /**
     * QUESTIONABLE: "Fix All" never applies suggestions, even with a fix kind that includes them.
     * The plugin requests the server's `source.fixAll.oxc`/`source.fixAllDangerous.oxc` action,
     * which only bundles fixes; suggestions (here: removing `debugger`) are only offered as
     * individual quick fixes. So `SAFE_FIX_OR_SUGGESTION` behaves like `SAFE_FIX` for Fix All.
     */
    fun testSafeFixOrSuggestionDoesNotApplySuggestions() {
        assertFixAll(OxlintFixKind.SAFE_FIX_OR_SUGGESTION, "const b = 2;\ndebugger;\nif (b == 2) {console.log(b);}\n")
    }

    fun testDangerousFix() {
        assertFixAll(OxlintFixKind.DANGEROUS_FIX, "const b = 2;\ndebugger;\nif (b === 2) {console.log(b);}\n")
    }

    /** QUESTIONABLE: suggestions are not applied; see [testSafeFixOrSuggestionDoesNotApplySuggestions]. */
    fun testDangerousFixOrSuggestionDoesNotApplySuggestions() {
        assertFixAll(OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION, "const b = 2;\ndebugger;\nif (b === 2) {console.log(b);}\n")
    }

    /** QUESTIONABLE: suggestions are not applied; see [testSafeFixOrSuggestionDoesNotApplySuggestions]. */
    fun testAllDoesNotApplySuggestions() {
        assertFixAll(OxlintFixKind.ALL, "const b = 2;\ndebugger;\nif (b === 2) {console.log(b);}\n")
    }

    fun testQuickFixesAreOfferedAtTheCaret() {
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")

        assertContainsElements(intentionsAt("let b"), "Replace `let b = 2;` with `const b = 2;`.",
            "Disable prefer-const for this line", "Disable prefer-const for this whole file")
    }

    fun testSuggestionsAreOfferedAsQuickFixesWhenIncluded() {
        OxlintSettings.getInstance(project).fixKind = OxlintFixKind.ALL
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(no-debugger)")

        assertContainsElements(intentionsAt("debugger"), "Remove the debugger statement")
    }

    fun testSuggestionsAreNotOfferedForSafeFixes() {
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(no-debugger)")

        val intentions = intentionsAt("debugger")
        assertContainsElements(intentions, "Disable no-debugger for this line")
        assertDoesntContain(intentions, "Remove the debugger statement")
    }

    fun testApplyingAQuickFix() {
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")

        intentionsAt("let b")
        myFixture.launchAction(myFixture.findSingleIntention("Replace `let b = 2;` with `const b = 2;`."))

        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.startsWith("const b = 2;"))
    }

    fun testNoneAppliesNothing() {
        OxlintSettings.getInstance(project).fixKind = OxlintFixKind.NONE
        val file = openFile("index.js")
        waitForOxlintServer()

        val applied = runInBackground { OxlintServerService.getInstance(project).fixAll(file, myFixture.editor.document) }

        assertFalse(applied)
        assertEquals(original, myFixture.editor.document.text)
    }

    fun testFixAllWorksRightAfterTyping() {
        val file = openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")

        // Typing invalidates the server's fix cache; fixAll must refresh it before asking for fixes.
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "let a = 1;\nconsole.log(a);\n")
        }
        val applied = runInBackground { OxlintServerService.getInstance(project).fixAll(file, myFixture.editor.document) }

        assertTrue(applied)
        assertEquals("const a = 1;\nconsole.log(a);\nconst b = 2;\ndebugger;\nif (b == 2) {console.log(b);}\n",
            myFixture.editor.document.text)
    }

    fun testFixAllActionAppliesFixesAndNotifies() {
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        assertEquals("const b = 2;\ndebugger;\nif (b == 2) {console.log(b);}\n", myFixture.editor.document.text)
        assertEquals(listOf("Fixes applied successfully"), notifications.map { it.title })
        assertEquals(NotificationType.INFORMATION, notifications.single().type)
    }

    fun testFixAllActionStaysQuietWhenNothingIsFixed() {
        OxlintSettings.getInstance(project).fixKind = OxlintFixKind.NONE
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxlintServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        assertEquals(original, myFixture.editor.document.text)
        assertEmpty(notifications)
    }

    fun testFixAllOnSaveIsOnlyEnabledWhenConfigured() {
        val action = OxlintFixAllOnSaveAction()
        assertFalse(action.isEnabledForProject(project))

        OxlintSettings.getInstance(project).fixAllOnSave = true
        assertTrue(action.isEnabledForProject(project))

        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED
        assertFalse(action.isEnabledForProject(project))
    }

    fun testFixAllOnSaveAppliesFixesWhenSaving() {
        OxlintSettings.getInstance(project).fixAllOnSave = true
        openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "\n") }

        // Actions on save only run for an explicit save, i.e. the Save All action (Ctrl/Cmd+S).
        myFixture.testAction(ActionManager.getInstance().getAction("SaveAll"))

        PlatformTestUtil.waitWithEventsDispatching("Fix All on save did not run", {
            document.text.startsWith("const b")
        }, 30)
        assertEquals("const b = 2;\ndebugger;\nif (b == 2) {console.log(b);}\n\n", document.text)
    }

    fun testFixAllOnSaveSkipsUnsupportedFiles() {
        OxlintSettings.getInstance(project).fixAllOnSave = true
        OxlintSettings.getInstance(project).supportedExtensions = mutableListOf(".ts")
        openFile("index.js")

        runInBackground { OxlintFixAllOnSaveAction().updateDocument(project, myFixture.editor.document) }

        assertEquals(original, myFixture.editor.document.text)
    }

    /** Moves the caret to [text] and returns the names of the intentions available there. */
    private fun intentionsAt(text: String): List<String> {
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf(text))
        var names = emptyList<String>()
        // Code actions are requested from the server asynchronously; retry until they arrive.
        PlatformTestUtil.waitWithEventsDispatching("No quick fixes from the server", {
            names = myFixture.availableIntentions.map { it.text }
            names.any { it.startsWith("Disable ") }
        }, 30)
        return names
    }

    private fun assertFixAll(fixKind: OxlintFixKind, expected: String) {
        OxlintSettings.getInstance(project).fixKind = fixKind
        val file = openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)", "eslint(eqeqeq)", "eslint(no-debugger)")

        val applied = runInBackground { OxlintServerService.getInstance(project).fixAll(file, myFixture.editor.document) }

        assertTrue(applied)
        assertEquals(expected, myFixture.editor.document.text)
    }
}
