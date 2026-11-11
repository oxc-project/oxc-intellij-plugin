package com.github.oxc.project.oxcintellijplugin.tool

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

/** What runs for a file. Nothing is started. */
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

  /**
   * A relative manual binary is under the server's root, wherever the server runs. Left relative, a
   * Docker, SSH or WSL interpreter would look for it in the IDE's own folder.
   */
  fun testRelativeManualBinaryIsUnderTheRoot() {
    myFixture.addFileToProject("tools/oxlint", "")

    for (setting in listOf("tools/oxlint", "./tools/oxlint", "src/../tools/oxlint")) {
      useOxlintBinary(setting)
      assertEquals(setting, path("tools/oxlint"), FileUtil.toSystemIndependentName(resolve(OxlintDefinition).executable))
    }
  }

  /** Such as `oxlint.exe` beside the project, which Windows would otherwise look for in the IDE's folder. */
  fun testBareNameOfAFileInTheRootIsUnderTheRoot() {
    myFixture.addFileToProject("oxlint.exe", "")
    useOxlintBinary("oxlint.exe")

    assertEquals(path("oxlint.exe"), FileUtil.toSystemIndependentName(resolve(OxlintDefinition).executable))
  }

  /** A bare name with no such file in the root is a command for the PATH lookup. */
  fun testBareCommandStaysACommand() {
    useOxlintBinary("oxlint")

    assertEquals("oxlint", resolve(OxlintDefinition).executable)
  }

  fun testAbsoluteManualBinaryIsUnchanged() {
    val binary = myFixture.addFileToProject("tools/oxlint", "").virtualFile
    useOxlintBinary(FileUtil.toSystemDependentName(binary.path))

    assertEquals(FileUtil.toSystemDependentName(binary.path), resolve(OxlintDefinition).executable)
  }

  fun testNothingToRunWithoutAPackage() {
    assertNull(ToolPackage(project, OxlintDefinition).resolve(file))
  }

  private fun useOxlintBinary(path: String) {
    OxlintSettings.getInstance(project).state.apply {
      configurationMode = ConfigurationMode.MANUAL
      binaryPath = path
    }
  }

  private fun resolve(definition: ToolDefinition): ServerLaunch =
    ToolPackage(project, definition).resolve(file) ?: throw AssertionError("Nothing to run for ${definition.displayName}")

  private fun projectDir(): VirtualFile = myFixture.tempDirFixture.getFile("")!!

  private fun path(relative: String): String = "${projectDir().path}/$relative"
}
