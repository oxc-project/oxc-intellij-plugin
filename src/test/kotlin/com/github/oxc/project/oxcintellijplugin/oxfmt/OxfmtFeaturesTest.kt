package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtFixAllOnSaveAction
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil

/**
 * Oxfmt against the real language server: the file types it formats, the IDE's Reformat Code,
 * "Fix All" and on-save formatting, and server lifecycle. Fixture: `testData/oxfmt/languages`.
 */
class OxfmtFeaturesTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("oxfmt/languages")
    }

    fun testJavaScript() = assertFormats("index.js", "const a = { b: 1, c: 'two' };\n")

    fun testTypeScript() = assertFormats("index.ts", "const a: number = 1;\nfunction f(x: number) {\n  return x;\n}\n")

    fun testJson() = assertFormats("data.json", "{ \"a\": 1, \"b\": [1, 2, 3] }\n")

    fun testCss() = assertFormats("styles.css", "a {\n  color: red;\n  margin: 0;\n}\n")

    fun testScss() = assertFormats("styles.scss", ".a {\n  .b {\n    color: red;\n  }\n}\n")

    fun testMarkdown() = assertFormats("README.md", "# Title\n\n- one\n- two\n")

    fun testYaml() = assertFormats("config.yaml", "a: 1\nb:\n  - x\n")

    fun testToml() = assertFormats("Cargo.toml", "[package]\nname = \"demo\"\nversion = \"1.0.0\"\n")

    fun testGraphql() = assertFormats("schema.graphql", "type Query {\n  a: Int\n}\n")

    fun testVue() = assertFormats("App.vue", "<script>\nconst a = { b: 1 };\n</script>\n<template><div>Hi</div></template>\n")

    fun testReformatCodeUsesOxfmt() {
        openFile("index.js")
        waitForOxfmtServer()

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)

        PlatformTestUtil.waitWithEventsDispatching("Reformat Code did not use Oxfmt", {
            myFixture.editor.document.text == "const a = { b: 1, c: 'two' };\n"
        }, 30)
    }

    fun testFixAllActionFormatsAndNotifies() {
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxfmtServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtApplyFixAllAction"))

        assertEquals("const a = { b: 1, c: 'two' };\n", myFixture.editor.document.text)
        assertEquals(listOf("Fixes applied successfully"), notifications.map { it.title })
    }

    /**
     * QUESTIONABLE: "Fix All" reports success even when there was nothing to format (the Oxlint
     * action only notifies when something changed).
     */
    fun testFixAllActionNotifiesEvenWhenNothingChanged() {
        writeProjectFile("index.js", "const a = { b: 1, c: 'two' };\n")
        val notifications = collectNotifications()
        openFile("index.js")
        waitForOxfmtServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtApplyFixAllAction"))

        assertEquals(listOf("Fixes applied successfully"), notifications.map { it.title })
    }

    fun testFixAllOnSaveIsOnlyEnabledWhenConfigured() {
        val action = OxfmtFixAllOnSaveAction()
        assertFalse(action.isEnabledForProject(project))

        OxfmtSettings.getInstance(project).fixAllOnSave = true
        assertTrue(action.isEnabledForProject(project))

        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED
        assertFalse(action.isEnabledForProject(project))
    }

    fun testFixAllOnSaveFormatsWhenSaving() {
        OxfmtSettings.getInstance(project).fixAllOnSave = true
        openFile("index.js")
        waitForOxfmtServer()
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "let x = 1\n") }

        // Actions on save only run for an explicit save, i.e. the Save All action (Ctrl/Cmd+S).
        myFixture.testAction(ActionManager.getInstance().getAction("SaveAll"))

        PlatformTestUtil.waitWithEventsDispatching("Fix All on save did not run", {
            document.text == "let x = 1;\nconst a = { b: 1, c: 'two' };\n"
        }, 30)
    }

    fun testFixAllOnSaveSkipsUnsupportedFiles() {
        OxfmtSettings.getInstance(project).supportedExtensions = mutableListOf(".ts")
        openFile("index.js")

        OxfmtFixAllOnSaveAction().processDocuments(project, arrayOf(myFixture.editor.document))

        assertEquals("const a = {b:1,c:\"two\"}\n", myFixture.editor.document.text)
    }

    fun testUnsupportedFilesDoNotStartTheServer() {
        OxfmtSettings.getInstance(project).supportedExtensions = mutableListOf(".ts")

        openFile("index.js")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEmpty(servers(OxfmtLspServerSupportProvider::class.java))
    }

    fun testDisabledModeDoesNotStartTheServer() {
        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED

        openFile("index.js")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEmpty(servers(OxfmtLspServerSupportProvider::class.java))
    }

    fun testManualBinaryPathIsUsed() {
        OxfmtSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            binaryPath = projectDir.resolve("node_modules/oxfmt/bin/oxfmt").toString()
        }

        assertEquals("const a = { b: 1, c: 'two' };\n", formatWithServer(openFile("index.js")))
    }

    fun testChangingTheConfigRestartsTheServerWithTheNewOptions() {
        val file = openFile("index.js")
        val before = waitForOxfmtServer()

        writeProjectFile(".oxfmtrc.json", """{ "semi": false }""")

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxfmtServer() !== before
        }, 30)
        assertEquals("const a = { b: 1, c: \"two\" }\n", formatWithServer(file))
    }

    fun testClosingTheLastSupportedFileStopsTheServer() {
        val file = openFile("index.js")
        waitForOxfmtServer()

        FileEditorManager.getInstance(project).closeFile(file)

        waitForNoRunningServer(OxfmtLspServerSupportProvider::class.java)
    }

    fun testRestartActionRestartsTheServerAndNotifies() {
        val notifications = collectNotifications()
        openFile("index.js")
        val before = waitForOxfmtServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtRestartServerAction"))

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxfmtServer() !== before
        }, 30)
        assertEquals(listOf("Oxfmt language server restarted."), notifications.map { it.title })
        assertEquals(NotificationType.INFORMATION, notifications.single().type)
    }

    private fun assertFormats(relativePath: String, expected: String) {
        assertEquals(expected, formatWithServer(openFile(relativePath)))
    }
}
