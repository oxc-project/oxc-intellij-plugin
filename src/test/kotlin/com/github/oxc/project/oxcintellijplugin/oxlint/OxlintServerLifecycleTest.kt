package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil

/** When the Oxlint server is started, restarted and stopped. */
class OxlintServerLifecycleTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("packagemanagers/oxc")
    }

    fun testChangingTheConfigRestartsTheServerWithTheNewRules() {
        openFile("index.js")
        val before = waitForOxlintServer()
        waitForOxlintRules("eslint(no-debugger)")

        writeProjectFile(".oxlintrc.json", """{ "rules": { "no-console": "error" } }""")

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxlintServer() !== before
        }, 30)
        waitForOxlintRules("eslint(no-console)")
    }

    fun testReplacingTheJsonConfigWithOxlintConfigTsRestartsTheServerWithItsRules() {
        openFile("index.js")
        val before = waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")

        com.intellij.openapi.application.WriteAction.runAndWait<Throwable> {
            projectFile(".oxlintrc.json").delete(this)
            val config = projectFile("index.js").parent.createChildData(this, "oxlint.config.ts")
            com.intellij.openapi.vfs.VfsUtil.saveText(config,
                "export default { rules: { \"no-console\": \"error\" } };\n")
        }

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxlintServer() !== before
        }, 30)
        waitForOxlintHighlights { highlights ->
            highlights.any { it.description.contains("eslint(no-console)") } &&
                highlights.none { it.description.contains("eslint(prefer-const)") }
        }
    }

    fun testAddingANestedConfigRestartsTheServer() {
        openFile("index.js")
        val before = waitForOxlintServer()

        val dir = projectFile("index.js").parent
        com.intellij.openapi.application.WriteAction.runAndWait<Throwable> {
            dir.createChildDirectory(this, "nested").createChildData(this, ".oxlintrc.json")
        }

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxlintServer() !== before
        }, 30)
    }

    fun testChangingAViteConfigRestartsTheServer() {
        openFile("index.js")
        val before = waitForOxlintServer()

        com.intellij.openapi.application.WriteAction.runAndWait<Throwable> {
            projectFile("index.js").parent.createChildData(this, "vite.config.ts")
        }

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxlintServer() !== before
        }, 30)
    }

    fun testClosingTheLastSupportedFileStopsTheServer() {
        val file = openFile("index.js")
        waitForOxlintServer()

        FileEditorManager.getInstance(project).closeFile(file)

        waitForNoRunningServer(OxlintLspServerSupportProvider::class.java)
    }

    fun testClosingAnUnsupportedFileKeepsTheServerRunning() {
        openFile("index.js")
        val server = waitForOxlintServer()
        val packageJson = projectFile("package.json")
        myFixture.openFileInEditor(packageJson)

        FileEditorManager.getInstance(project).closeFile(packageJson)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertSame(server, waitForOxlintServer())
    }

    fun testRestartActionRestartsTheServerAndNotifies() {
        val notifications = collectNotifications()
        openFile("index.js")
        val before = waitForOxlintServer()

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintRestartServerAction"))

        PlatformTestUtil.waitWithEventsDispatching("Server was not restarted", {
            waitForOxlintServer() !== before
        }, 30)
        assertEquals(listOf("Oxlint language server restarted."), notifications.map { it.title })
    }
}
