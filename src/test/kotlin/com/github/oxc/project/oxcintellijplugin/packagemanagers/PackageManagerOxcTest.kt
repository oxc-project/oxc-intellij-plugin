package com.github.oxc.project.oxcintellijplugin.packagemanagers

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.PackageManager
import java.nio.file.Path

/**
 * Verifies that Oxlint and Oxfmt are found and work end to end when installed by each supported
 * package manager. The fixture is `testData/packagemanagers/oxc`.
 */
abstract class PackageManagerOxcTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("packagemanagers/oxc")
    }

    fun testOxlintAndOxfmtPackagesAreResolvedFromTheProject() {
        val file = projectFile("index.js")

        val oxlint = OxlintPackage(project).binaryPath(file, null)
        val oxfmt = OxfmtPackage(project).binaryPath(file, null)

        assertNotNull("oxlint binary not found with $packageManager", oxlint)
        assertNotNull("oxfmt binary not found with $packageManager", oxfmt)
        assertTrue("$oxlint is not inside the project", isInsideProject(oxlint!!))
        assertTrue("$oxfmt is not inside the project", isInsideProject(oxfmt!!))
    }

    fun testOxlintServerUsesTheProjectDirectoryAsRoot() {
        openFile("index.js")

        val server = waitForOxlintServer()

        assertEquals(listOf(realPath(projectDir)), server.descriptor.roots.map { realPath(it.toNioPath()) })
    }

    fun testOxlintReportsDiagnostics() {
        openFile("index.js")
        waitForOxlintServer()

        val highlights = waitForOxlintRules("eslint(no-debugger)", "eslint(prefer-const)")

        assertTrue(highlights.any { it.description.contains("`debugger` statement is not allowed") })
    }

    fun testOxlintFixAllAppliesSafeFixes() {
        val file = openFile("index.js")
        waitForOxlintServer()
        waitForOxlintRules("eslint(prefer-const)")

        val applied = runInBackground { OxlintServerService.getInstance(project).fixAll(file, myFixture.editor.document) }

        assertTrue(applied)
        assertEquals("const greeting = \"Happy developing ✨\"\ndebugger\nconsole.log(greeting)\n",
            myFixture.editor.document.text)
    }

    fun testOxfmtServerUsesTheProjectDirectoryAsRoot() {
        openFile("index.js")

        val server = waitForOxfmtServer()

        assertEquals(listOf(realPath(projectDir)), server.descriptor.roots.map { realPath(it.toNioPath()) })
    }

    fun testOxfmtFormatsWithTheProjectConfig() {
        val file = openFile("index.js")

        assertEquals("let greeting = 'Happy developing ✨';\ndebugger;\nconsole.log(greeting);\n",
            formatWithServer(file))
    }

    fun testOxfmtServiceFixAllFormatsTheDocument() {
        val file = openFile("index.js")
        waitForOxfmtServer()

        runInBackground { OxfmtServerService.getInstance(project).fixAll(file, myFixture.editor.document) }

        assertEquals("let greeting = 'Happy developing ✨';\ndebugger;\nconsole.log(greeting);\n",
            myFixture.editor.document.text)
    }

    private fun isInsideProject(path: String): Boolean =
        Path.of(path).toAbsolutePath().normalize().startsWith(projectDir.toAbsolutePath().normalize()) ||
        Path.of(path).toAbsolutePath().normalize().startsWith(realPath(projectDir))

    private fun realPath(path: Path): Path = path.toRealPath()
}

class NpmOxcTest : PackageManagerOxcTest() {
    override val packageManager = PackageManager.NPM
}

class PnpmOxcTest : PackageManagerOxcTest() {
    override val packageManager = PackageManager.PNPM
}

class YarnOxcTest : PackageManagerOxcTest() {
    override val packageManager = PackageManager.YARN
}

class BunOxcTest : PackageManagerOxcTest() {
    override val packageManager = PackageManager.BUN
}
