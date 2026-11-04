package com.github.oxc.project.oxcintellijplugin.packagemanagers

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.PackageManager
import java.nio.file.Path

/**
 * Verifies Vite+ projects with each package manager: the plugin must launch `vp lint --lsp` and
 * `vp fmt --lsp` from the project's `vite-plus` package so the `lint`/`fmt` sections of
 * `vite.config.ts` apply. The fixture is `testData/packagemanagers/vite-plus`.
 */
abstract class PackageManagerVitePlusTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("packagemanagers/vite-plus")
    }

    fun testVitePlusExecutableIsPreferred() {
        val file = projectFile("index.js")
        val oxlint = OxlintPackage(project)
        val oxfmt = OxfmtPackage(project)

        val lintVitePlus = oxlint.vitePlusPackage(file)
        val fmtVitePlus = oxfmt.vitePlusPackage(file)
        assertNotNull("vite-plus not found with $packageManager", lintVitePlus)
        assertNotNull("vite-plus not found with $packageManager", fmtVitePlus)

        val lintBinary = oxlint.binaryPath(file, lintVitePlus)!!
        val fmtBinary = oxfmt.binaryPath(file, fmtVitePlus)!!
        assertTrue(lintBinary, Path.of(lintBinary).endsWith(Path.of("bin", "vp")))
        assertTrue(fmtBinary, Path.of(fmtBinary).endsWith(Path.of("bin", "vp")))

        assertEquals(listOf("lint", "--lsp"), oxlint.binaryParameters(lintVitePlus).values())
        assertEquals(listOf("fmt", "--lsp"), oxfmt.binaryParameters(file, fmtVitePlus).values())
    }

    fun testManualBinaryPathTakesPrecedenceOverVitePlus() {
        val file = projectFile("index.js")
        OxlintSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            binaryPath = "/custom/oxlint"
        }
        OxfmtSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            binaryPath = "/custom/oxfmt"
        }

        assertNull(OxlintPackage(project).vitePlusPackage(file))
        assertNull(OxfmtPackage(project).vitePlusPackage(file))
        assertEquals("/custom/oxlint", OxlintPackage(project).binaryPath(file, null))
        assertEquals(listOf("--lsp"), OxlintPackage(project).binaryParameters(null).values())
    }

    fun testLintRulesFromViteConfigAreReported() {
        openFile("index.js")
        waitForOxlintServer()

        val highlights = waitForOxlintRules("eslint(no-debugger)")

        // vite.config.ts raises no-debugger from the default "warn" to "error".
        assertEquals(com.intellij.lang.annotation.HighlightSeverity.ERROR,
            highlights.first { it.description.contains("eslint(no-debugger)") }.severity)
    }

    fun testFormatOptionsFromViteConfigAreUsed() {
        val file = openFile("index.js")

        assertEquals("const greeting = 'Happy developing ✨';\ndebugger;\nconsole.log(greeting);\n",
            formatWithServer(file))
    }

    private fun List<ProcessCommandParameter>.values() = map { it.toString() }
}

class NpmVitePlusTest : PackageManagerVitePlusTest() {
    override val packageManager = PackageManager.NPM
}

class PnpmVitePlusTest : PackageManagerVitePlusTest() {
    override val packageManager = PackageManager.PNPM
}

class YarnVitePlusTest : PackageManagerVitePlusTest() {
    override val packageManager = PackageManager.YARN
}

class BunVitePlusTest : PackageManagerVitePlusTest() {
    override val packageManager = PackageManager.BUN
}
