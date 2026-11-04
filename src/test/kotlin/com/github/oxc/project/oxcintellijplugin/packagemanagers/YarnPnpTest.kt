package com.github.oxc.project.oxcintellijplugin.packagemanagers

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.PackageManager
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpNodePackage
import com.intellij.testFramework.PlatformTestUtil

/**
 * Yarn Plug'n'Play installs have no `node_modules`. The plugin has a dedicated branch for
 * [YarnPnpNodePackage] when picking the server root, so PnP is something it intends to support.
 */
class YarnPnpTest : OxcLspTestCase() {

    override val packageManager = PackageManager.YARN_PNP

    override fun setUp() {
        super.setUp()
        setUpFixture("packagemanagers/oxc")
    }

    fun testPackagesAreDetectedAsYarnPnpPackages() {
        val file = projectFile("index.js")

        assertInstanceOf(OxlintPackage(project).getPackage(file), YarnPnpNodePackage::class.java)
        assertInstanceOf(OxfmtPackage(project).getPackage(file), YarnPnpNodePackage::class.java)
    }

    /**
     * QUESTIONABLE: with Yarn PnP no executable is resolved, so neither language server starts and
     * the plugin silently does nothing.
     *
     * The plugin turns the package into `<package path>/bin/oxlint` via
     * `getAbsolutePackagePathToRequire`, which is null for a PnP package here. Even when it is
     * not, the path points into a zip archive in `.yarn/cache` that plain `node` cannot execute
     * without the PnP runtime (`yarn node` / `--require .pnp.cjs`), which the plugin never sets up.
     * Note that the IDE's PnP dependency tree does not finish loading in the test environment, so
     * this documents what the plugin does with the information available, not a full IDE session.
     */
    fun testNoExecutableIsResolvedSoNoServerStarts() {
        val file = projectFile("index.js")

        assertNull(OxlintPackage(project).binaryPath(file, null))
        assertNull(OxfmtPackage(project).binaryPath(file, null))

        openFile("index.js")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEmpty(servers(OxlintLspServerSupportProvider::class.java))
    }
}
