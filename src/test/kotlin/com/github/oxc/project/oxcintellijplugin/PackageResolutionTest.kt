package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import java.nio.file.Path

/**
 * How the configuration mode decides which binary and config file are used. The fixture has
 * `oxlint` and `oxfmt` installed with npm; package manager specifics are in `packagemanagers`.
 */
class PackageResolutionTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("packagemanagers/oxc")
    }

    fun testAutomaticModeUsesTheInstalledPackages() {
        val file = projectFile("index.js")

        assertEquals(projectDir.resolve("node_modules/oxlint/bin/oxlint").normalized(),
            Path.of(OxlintPackage(project).binaryPath(file, null)!!).normalized())
        assertEquals(projectDir.resolve("node_modules/oxfmt/bin/oxfmt").normalized(),
            Path.of(OxfmtPackage(project).binaryPath(file, null)!!).normalized())
        assertNull(OxlintPackage(project).configPath())
        assertNull(OxfmtPackage(project).configPath())
        assertEquals(listOf("--lsp"), OxlintPackage(project).binaryParameters(null).map { it.toString() })
        assertEquals(listOf("--lsp"), OxfmtPackage(project).binaryParameters(file, null).map { it.toString() })
    }

    fun testAutomaticModeIgnoresManualPaths() {
        val file = projectFile("index.js")
        OxlintSettings.getInstance(project).binaryPath = "/custom/oxlint"
        OxlintSettings.getInstance(project).configPath = "/custom/.oxlintrc.json"

        assertFalse(OxlintPackage(project).binaryPath(file, null) == "/custom/oxlint")
        assertNull(OxlintPackage(project).configPath())
    }

    fun testManualModeUsesTheConfiguredPaths() {
        val file = projectFile("index.js")
        OxlintSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            binaryPath = "/custom/oxlint"
            configPath = "/custom/.oxlintrc.json"
        }
        OxfmtSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            binaryPath = "/custom/oxfmt"
            configPath = "/custom/.oxfmtrc.json"
        }

        assertEquals("/custom/oxlint", OxlintPackage(project).binaryPath(file, null))
        assertEquals("/custom/.oxlintrc.json", OxlintPackage(project).configPath())
        assertEquals("/custom/oxfmt", OxfmtPackage(project).binaryPath(file, null))
        assertEquals("/custom/.oxfmtrc.json", OxfmtPackage(project).configPath())
    }

    fun testManualModeWithoutBinaryFallsBackToTheInstalledPackage() {
        val file = projectFile("index.js")
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.MANUAL
        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.MANUAL

        assertTrue(OxlintPackage(project).binaryPath(file, null)!!.endsWith("oxlint"))
        assertTrue(OxfmtPackage(project).binaryPath(file, null)!!.endsWith("oxfmt"))
    }

    fun testDisabledModeResolvesNothing() {
        val file = projectFile("index.js")
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED
        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED

        assertFalse(OxlintPackage(project).isEnabled())
        assertFalse(OxfmtPackage(project).isEnabled())
        assertNull(OxlintPackage(project).binaryPath(file, null))
        assertNull(OxfmtPackage(project).binaryPath(file, null))
        assertNull(OxlintPackage(project).vitePlusPackage(file))
        assertNull(OxfmtPackage(project).vitePlusPackage(file))
    }

    fun testVitePlusIsNotUsedWhenNotInstalled() {
        val file = projectFile("index.js")

        assertNull(OxlintPackage(project).vitePlusPackage(file))
        assertNull(OxfmtPackage(project).vitePlusPackage(file))
    }

    private fun Path.normalized(): Path = toAbsolutePath().normalize()
}
