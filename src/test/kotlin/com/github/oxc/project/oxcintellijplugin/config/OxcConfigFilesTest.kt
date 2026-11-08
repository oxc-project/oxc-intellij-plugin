package com.github.oxc.project.oxcintellijplugin.config

import com.github.oxc.project.oxcintellijplugin.backend.config.OxcConfigFileIconProvider
import com.github.oxc.project.oxcintellijplugin.backend.config.OxcSchemaCatalogExclusion
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.github.oxc.project.oxcintellijplugin.testutil.FakePackages
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection
import java.nio.file.Files

/** Spec section 11: which files get the Oxc icon, and which schema each config gets. */
class OxcConfigFilesTest : OxcFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    // The JavaScript plugin only looks in node_modules next to a package.json.
    myFixture.addFileToProject("package.json", """{ "name": "app" }""")
  }

  fun testConfigNamesGetTheIcon() {
    for (name in listOf(
      ".oxlintrc.json", ".oxlintrc.jsonc", "oxlint.config.ts", "oxlint.config.mts",
      ".oxfmtrc.json", ".oxfmtrc.jsonc", "oxfmt.config.ts", "oxfmt.config.mts",
    )) {
      assertSame(name, OxcIcons.OxcRound, icon(file(name)))
    }
  }

  fun testOtherFilesDontGetTheIcon() {
    for (name in listOf("vite.config.ts", "package.json", "oxlintrc.json", "custom.json")) {
      assertNull(name, icon(file(name)))
    }
  }

  fun testManualConfigPathsGetTheIcon() {
    val lint = file("configs/lint.json")
    val fmt = file("configs/fmt.json")
    useOxlintConfig(lint.path)
    useOxfmtConfig("configs/fmt.json")

    assertSame(OxcIcons.OxcRound, icon(lint))
    assertSame("A relative path is resolved from the project's folder", OxcIcons.OxcRound, icon(fmt))
  }

  /** The path is only used in Manual mode, so in Automatic mode it doesn't point at a config. */
  fun testConfigPathOutsideManualModeDoesntGetTheIcon() {
    val fmt = file("configs/fmt.json")
    useOxfmtConfig(fmt.path)
    OxfmtSettings.getInstance(project).state.configurationMode = ConfigurationMode.AUTOMATIC

    assertNull(icon(fmt))
  }

  fun testSchemaComesFromTheInstalledPackage() {
    val lintSchema = FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint")
    val fmtSchema = FakePackages.tool(myFixture, "node_modules/oxfmt", "oxfmt")

    assertEquals(listOf(lintSchema), schemas(file(".oxlintrc.json")))
    assertEquals(listOf(lintSchema), schemas(file(".oxlintrc.jsonc")))
    assertEquals(listOf(fmtSchema), schemas(file(".oxfmtrc.json")))
    assertEquals(listOf(fmtSchema), schemas(file(".oxfmtrc.jsonc")))
  }

  /** The platform checks the config against the schema, which is what the user sees. */
  fun testConfigIsCheckedAgainstTheSchema() {
    FakePackages.tool(myFixture, "node_modules/oxfmt", "oxfmt", schema = """
      { "type": "object", "properties": { "semi": { "type": "boolean" } } }
    """.trimIndent())
    myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
    myFixture.configureByText(".oxfmtrc.json", """{ "semi": <warning descr="Incompatible types.
 Required: boolean. Actual: string.">"no"</warning> }""")

    myFixture.checkHighlighting()
  }

  /** A nested package can have its own version of the tool, and each config gets its own version's schema. */
  fun testNestedPackageGetsItsOwnSchema() {
    val root = FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint", "1.0.0")
    myFixture.addFileToProject("packages/app/package.json", """{ "name": "nested" }""")
    val nested = FakePackages.tool(myFixture, "packages/app/node_modules/oxlint", "oxlint", "2.0.0")

    assertEquals(listOf(root), schemas(file(".oxlintrc.json")))
    assertEquals(listOf(nested), schemas(file("packages/app/.oxlintrc.json")))
  }

  /** The catalog's schema is excluded too, because it may not match whatever the user installs later. */
  fun testNoSchemaWithoutAPackage() {
    val config = file(".oxlintrc.json")

    assertEmpty(schemas(config))
    assertTrue(OxcSchemaCatalogExclusion().isExcluded(config))
  }

  fun testSchemaAppearsWhenThePackageIsInstalled() {
    val config = file(".oxfmtrc.json")
    assertEmpty(schemas(config))

    val schema = FakePackages.tool(myFixture, "node_modules/oxfmt", "oxfmt")

    assertEquals(listOf(schema), schemas(config))
  }

  /** `vp` asks Node for the tool from the server's folder first, so the project's own copy wins. */
  fun testVitePlusUsesTheProjectsTool() {
    FakePackages.vitePlus(myFixture)
    FakePackages.tool(myFixture, "node_modules/vite-plus/node_modules/oxlint", "oxlint", "1.91.0")
    val hoisted = FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint", "1.92.0")

    assertEquals(listOf(hoisted), schemas(file(".oxlintrc.json")))
  }

  /** Then from `vite-plus` itself, which finds the copy that it depends on. */
  fun testVitePlusUsesItsOwnTool() {
    FakePackages.vitePlus(myFixture)
    val own = FakePackages.tool(myFixture, "node_modules/vite-plus/node_modules/oxfmt", "oxfmt", "0.76.0")

    assertEquals(listOf(own), schemas(file(".oxfmtrc.json")))
  }

  /** pnpm keeps `vite-plus` and its dependencies side by side, behind a link. */
  fun testVitePlusUsesItsOwnToolBehindALink() {
    // Creating a link on Windows needs a privilege that CI doesn't have.
    if (SystemInfo.isWindows) return
    val store = "node_modules/.pnpm/vite-plus@1.5.0/node_modules"
    FakePackages.vitePlus(myFixture, "$store/vite-plus")
    val own = FakePackages.tool(myFixture, "$store/oxlint", "oxlint", "1.91.0")
    link("node_modules/vite-plus", "$store/vite-plus")

    assertEquals(listOf(own), schemas(file(".oxlintrc.json")))
  }

  fun testManualBinaryUsesItsPackagesSchema() {
    FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint", "1.0.0")
    val manual = FakePackages.tool(myFixture, "tools/oxlint", "oxlint", "2.0.0")
    useOxlintBinary(manual.parent.findFileByRelativePath("bin/oxlint")!!.path)

    assertEquals(listOf(manual), schemas(file(".oxlintrc.json")))
  }

  /** A downloaded binary isn't in a package, and the project's `package.json` isn't the tool's. */
  fun testManualBinaryOutsideAPackageGetsNoSchema() {
    FakePackages.tool(myFixture, "node_modules/oxlint", "oxlint")
    useOxlintBinary(file("tools/oxlint").path)

    assertEmpty(schemas(file(".oxlintrc.json")))
  }

  fun testManualConfigPathGetsTheSchema() {
    val schema = FakePackages.tool(myFixture, "node_modules/oxfmt", "oxfmt")
    val config = file("configs/fmt.json")
    assertEmpty(schemas(config))

    useOxfmtConfig("configs/fmt.json")

    assertEquals(listOf(schema), schemas(config))
  }

  private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "{}").virtualFile

  private fun icon(file: VirtualFile) = OxcConfigFileIconProvider().getIcon(file, 0, project)

  private fun schemas(file: VirtualFile): List<VirtualFile> =
    JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file).toList()

  private fun link(path: String, target: String) {
    val folder = myFixture.tempDirFixture.getFile("")!!
    val linkPath = folder.toNioPath().resolve(path)
    Files.createDirectories(linkPath.parent)
    Files.createSymbolicLink(linkPath, folder.toNioPath().resolve(target))
    WriteAction.runAndWait<Exception> { folder.refresh(false, true) }
  }

  private fun useOxlintBinary(path: String) {
    val settings = OxlintSettings.getInstance(project)
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.MANUAL, binaryPath = path))
  }

  private fun useOxlintConfig(path: String) {
    val settings = OxlintSettings.getInstance(project)
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.MANUAL, configPath = path))
  }

  private fun useOxfmtConfig(path: String) {
    val settings = OxfmtSettings.getInstance(project)
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.MANUAL, configPath = path))
  }
}
