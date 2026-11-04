package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.PackageManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.testFramework.PlatformTestUtil

/**
 * A project holding two independent packages (`app-a`, `app-b`), each with its own `oxlint` and
 * `oxfmt` install and config, as in a repository of separate apps. Each package must get its own
 * servers, rooted at the package, using the package's config. Fixtures: `testData/monorepo/app-*`.
 */
abstract class MonorepoTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("monorepo/app-a", "app-a")
        setUpFixture("monorepo/app-b", "app-b")
    }

    fun testEachPackageGetsItsOwnOxlintServer() {
        openFile("app-a/index.js")
        openFile("app-b/index.js")

        assertEquals(setOf("app-a", "app-b"), waitForServerRoots(OxlintLspServerSupportProvider::class.java))
    }

    fun testEachPackageGetsItsOwnOxfmtServer() {
        openFile("app-a/index.js")
        openFile("app-b/index.js")

        assertEquals(setOf("app-a", "app-b"), waitForServerRoots(OxfmtLspServerSupportProvider::class.java))
    }

    fun testEachPackageIsLintedWithItsOwnConfig() {
        // app-a raises no-console to an error; app-b makes it a warning and turns no-debugger off.
        openFile("app-a/index.js")
        val a = waitForOxlintRules("eslint(no-console)", "eslint(no-debugger)")
        assertEquals(HighlightSeverity.ERROR, a.first { it.description.contains("eslint(no-console)") }.severity)

        openFile("app-b/index.js")
        val b = waitForOxlintRules("eslint(no-console)")
        assertEquals(HighlightSeverity.WARNING, b.first { it.description.contains("eslint(no-console)") }.severity)
        assertTrue(b.none { it.description.contains("eslint(no-debugger)") })
    }

    fun testEachPackageIsFormattedWithItsOwnConfig() {
        val a = openFile("app-a/index.js")
        assertEquals("let greeting = \"Happy developing ✨\";\ndebugger;\nconsole.log(greeting);\n", formatWithServer(a))

        val b = openFile("app-b/index.js")
        assertEquals("let greeting = \"Happy developing ✨\"\ndebugger\nconsole.log(greeting)\n", formatWithServer(b))
    }

    /** Waits until two servers of [provider] run and returns their roots relative to the project. */
    private fun waitForServerRoots(provider: Class<out LspServerSupportProvider>): Set<String> {
        PlatformTestUtil.waitWithEventsDispatching({ "Server roots: ${serverRoots(provider)}" }, {
            serverRoots(provider).size >= 2
        }, 60)
        return serverRoots(provider)
    }

    private fun serverRoots(provider: Class<out LspServerSupportProvider>): Set<String> =
        servers(provider).filter { it.state == LspServerState.Running }
            .flatMap { server -> server.descriptor.roots.map { projectDir.relativize(it.toNioPath()).toString() } }
            .toSet()
}

class NpmMonorepoTest : MonorepoTest() {
    override val packageManager = PackageManager.NPM
}

class PnpmMonorepoTest : MonorepoTest() {
    override val packageManager = PackageManager.PNPM
}
