package com.github.oxc.project.oxcintellijplugin.config

import com.github.oxc.project.oxcintellijplugin.backend.config.OxcConfigFileIconProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.intellij.openapi.vfs.VirtualFile

/** Spec section 11: which files get the Oxc icon. */
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

  private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "{}").virtualFile

  private fun icon(file: VirtualFile) = OxcConfigFileIconProvider().getIcon(file, 0, project)

  private fun useOxlintConfig(path: String) {
    val settings = OxlintSettings.getInstance(project)
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.MANUAL, configPath = path))
  }

  private fun useOxfmtConfig(path: String) {
    val settings = OxfmtSettings.getInstance(project)
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.MANUAL, configPath = path))
  }
}
