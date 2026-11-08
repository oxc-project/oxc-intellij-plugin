package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.ServerLaunch
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolPackage
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.FakePackages
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/** What runs for a file (spec section 5), including Vite+. Nothing is started. */
class ToolPackageTest : OxcFixtureTestCase() {

  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    // The JavaScript plugin only looks in node_modules next to a package.json.
    myFixture.addFileToProject("package.json", """{ "name": "app" }""")
    file = myFixture.addFileToProject("src/index.js", "debugger;\n").virtualFile
  }

  fun testToolPackageRunsWithLsp() {
    FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint")

    val launch = resolve(OxlintDefinition)

    assertEquals(path("node_modules/oxlint/bin/oxlint"), FileUtil.toSystemIndependentName(launch.executable))
    assertEquals(listOf("--lsp"), launch.parameters)
    assertEquals(projectDir(), launch.root)
  }

  fun testVitePlusRunsLintAndFmt() {
    FakePackages.vitePlus(myFixture)

    val lint = resolve(OxlintDefinition)
    val fmt = resolve(OxfmtDefinition)

    assertEquals(path("node_modules/vite-plus/bin/vp"), FileUtil.toSystemIndependentName(lint.executable))
    assertEquals(listOf("lint", "--lsp"), lint.parameters)
    assertEquals(path("node_modules/vite-plus/bin/vp"), FileUtil.toSystemIndependentName(fmt.executable))
    assertEquals(listOf("fmt", "--lsp"), fmt.parameters)
    assertEquals(projectDir(), lint.root)
  }

  /** npm hoists the tools out of `vite-plus`, and only `vp` reads `vite.config.ts` (FR-RES-2). */
  fun testVitePlusWinsOverAnInstalledTool() {
    FakePackages.vitePlus(myFixture)
    FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint")

    assertEquals(listOf("lint", "--lsp"), resolve(OxlintDefinition).parameters)
  }

  fun testVitePlusRunsInManualModeWithNoBinary() {
    FakePackages.vitePlus(myFixture)
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.MANUAL

    assertEquals(listOf("lint", "--lsp"), resolve(OxlintDefinition).parameters)
  }

  /** FR-RES-3: a manual binary never gets a subcommand or Vite+. */
  fun testManualBinaryIgnoresVitePlus() {
    FakePackages.vitePlus(myFixture)
    val binary = myFixture.addFileToProject("tools/oxfmt", "").virtualFile
    OxfmtSettings.getInstance(project).state.apply {
      configurationMode = ConfigurationMode.MANUAL
      binaryPath = binary.path
    }

    val launch = resolve(OxfmtDefinition)

    assertEquals(binary.path, FileUtil.toSystemIndependentName(launch.executable))
    assertEquals(listOf("--lsp"), launch.parameters)
  }

  /** A nested package's own `vite-plus` runs from that package, as a nested `oxlint` would. */
  fun testNearestVitePlusWins() {
    FakePackages.vitePlus(myFixture)
    myFixture.addFileToProject("src/package.json", """{ "name": "nested" }""")
    FakePackages.vitePlus(myFixture, "src/node_modules/vite-plus")

    val launch = resolve(OxlintDefinition)

    assertEquals(path("src/node_modules/vite-plus/bin/vp"), FileUtil.toSystemIndependentName(launch.executable))
    assertEquals(file.parent, launch.root)
  }

  fun testNothingToRunWithoutAPackage() {
    assertNull(ToolPackage(project, OxlintDefinition).resolve(file))
  }

  private fun resolve(definition: ToolDefinition): ServerLaunch =
    ToolPackage(project, definition).resolve(file) ?: throw AssertionError("Nothing to run for ${definition.displayName}")

  private fun projectDir(): VirtualFile = myFixture.tempDirFixture.getFile("")!!

  private fun path(relative: String): String = "${projectDir().path}/$relative"
}
