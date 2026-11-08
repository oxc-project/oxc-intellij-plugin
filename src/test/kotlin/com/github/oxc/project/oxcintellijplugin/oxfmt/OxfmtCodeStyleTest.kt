package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtCodeStyle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtCodeStyleSettingsModifier
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtTrailingComma
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.findOxfmtConfig
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.importOxfmtCodeStyle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.application.options.CodeStyle
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.lang.typescript.formatter.TypeScriptCodeStyleSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking

private const val CONFIG = """{ "singleQuote": true, "semi": false, "tabWidth": 7, "useTabs": true, "printWidth": 77, "trailingComma": "none", "bracketSpacing": false }"""

class OxfmtCodeStyleTest : OxcFixtureTestCase() {

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  fun testEveryKeyIsRead() {
    assertEquals(
      OxfmtConfig(
        bracketSpacing = false,
        printWidth = 77,
        semi = false,
        singleQuote = true,
        tabWidth = 7,
        trailingComma = OxfmtTrailingComma.NONE,
        useTabs = true,
      ),
      parse(CONFIG),
    )
  }

  fun testDefaultsMatchOxfmt() {
    assertEquals(OxfmtConfig(true, 100, true, false, 2, OxfmtTrailingComma.ALL, false), parse("{}"))
  }

  fun testWrongTypesAndNullAreTreatedAsMissing() {
    assertEquals(OxfmtConfig(), parse("""{ "semi": "no", "tabWidth": "four", "singleQuote": null, "useTabs": 1 }"""))
  }

  fun testCommentsAreAllowed() {
    assertEquals(3, parse("{\n  // indent\n  \"tabWidth\": 3 /* spaces */\n}").tabWidth)
  }

  fun testEmptyFileAndNonObjectAreDefaults() {
    assertEquals(OxfmtConfig(), parse(""))
    assertEquals(OxfmtConfig(), parse("[1, 2]"))
    assertEquals(OxfmtConfig(), parse("{ not json"))
  }

  fun testUnknownTrailingCommaIsTheDefault() {
    assertEquals(OxfmtTrailingComma.ALL, parse("""{ "trailingComma": "sometimes" }""").trailingComma)
    assertEquals(OxfmtTrailingComma.ES5, parse("""{ "trailingComma": "es5" }""").trailingComma)
  }

  fun testApplyingMakesItApplied() {
    val copy = CodeStyle.createTestSettings()
    val config = parse(CONFIG)
    assertFalse(OxfmtCodeStyle.isApplied(copy, config))

    assertTrue(OxfmtCodeStyle.apply(copy, config))

    assertTrue(OxfmtCodeStyle.isApplied(copy, config))
    assertFalse("Nothing is left to change", OxfmtCodeStyle.apply(copy, config))
  }

  fun testAppliesToBothLanguages() {
    val copy = CodeStyle.createTestSettings()

    OxfmtCodeStyle.apply(copy, parse(CONFIG))

    for (language in listOf(JavascriptLanguage, JavaScriptSupportLoader.TYPESCRIPT)) {
      val indent = copy.getCommonSettings(language).indentOptions!!
      assertEquals(7, indent.INDENT_SIZE)
      assertEquals(7, indent.CONTINUATION_INDENT_SIZE)
      assertEquals(7, indent.TAB_SIZE)
      assertTrue(indent.USE_TAB_CHARACTER)
      assertEquals(77, copy.getRightMargin(language))
      assertEquals(listOf(77), copy.getSoftMargins(language))
    }
    for (style in listOf(JSCodeStyleSettings::class.java, TypeScriptCodeStyleSettings::class.java)) {
      val custom = copy.getCustomSettings(style)
      assertFalse(custom.USE_DOUBLE_QUOTES)
      assertFalse(custom.USE_SEMICOLON_AFTER_STATEMENT)
      assertFalse(custom.SPACES_WITHIN_OBJECT_LITERAL_BRACES)
      assertFalse(custom.SPACES_WITHIN_OBJECT_TYPE_BRACES)
      assertFalse(custom.SPACES_WITHIN_IMPORTS)
      assertEquals(JSCodeStyleSettings.TrailingCommaOption.Remove, custom.ENFORCE_TRAILING_COMMA)
      assertTrue(custom.FORCE_QUOTE_STYlE)
      assertTrue(custom.FORCE_SEMICOLON_STYLE)
      assertTrue(custom.SPACE_BEFORE_FUNCTION_LEFT_PARENTH)
    }
  }

  fun testNearestConfigWins() {
    val nearest = myFixture.addFileToProject("a/.oxfmtrc.jsonc", "{}").virtualFile
    myFixture.addFileToProject(".oxfmtrc.json", "{}")
    val file = myFixture.addFileToProject("a/b/index.js", "").virtualFile

    assertEquals(nearest, nearestConfig(file))
  }

  fun testASelectedFolderCountsAsTheFirstPlaceToLook() {
    val config = myFixture.addFileToProject("a/.oxfmtrc.json", "{}").virtualFile

    assertEquals(config, nearestConfig(config.parent))
  }

  fun testNoConfig() {
    assertNull(nearestConfig(myFixture.addFileToProject("index.js", "").virtualFile))
  }

  fun testManualConfigPathWinsOverANearerConfig() {
    val manual = myFixture.addFileToProject("configs/team.json", "{}").virtualFile
    myFixture.addFileToProject("a/.oxfmtrc.json", "{}")
    useManualConfig("configs/team.json")

    assertEquals(manual, nearestConfig(myFixture.addFileToProject("a/index.js", "").virtualFile))
  }

  fun testMissingManualConfigMeansNoConfig() {
    myFixture.addFileToProject(".oxfmtrc.json", "{}")
    useManualConfig("missing.json")

    assertNull(nearestConfig(myFixture.addFileToProject("index.js", "").virtualFile))
  }

  fun testRootConfigIsUsedWhenNestedConfigsAreOff() {
    val root = myFixture.addFileToProject(".oxfmtrc.json", "{}").virtualFile
    myFixture.addFileToProject("a/.oxfmtrc.json", "{}")
    settings.state.disableNestedConfig = true

    assertEquals(root, nearestConfig(myFixture.addFileToProject("a/index.js", "").virtualFile))
  }

  /** `OxfmtJsConfigTest` covers reading it. */
  fun testNearerTypeScriptConfigHidesTheRootConfig() {
    myFixture.addFileToProject(".oxfmtrc.json", CONFIG)
    val typeScript = myFixture.addFileToProject("a/oxfmt.config.ts", "export default {}").virtualFile
    val file = myFixture.addFileToProject("a/index.js", "")

    assertEquals(typeScript, nearestConfig(file.virtualFile))
  }

  fun testFolderWithTwoConfigsFallsBackToTheRootConfig() {
    val root = myFixture.addFileToProject(".oxfmtrc.json", "{}").virtualFile
    myFixture.addFileToProject("a/.oxfmtrc.json", "{}")
    myFixture.addFileToProject("a/.oxfmtrc.jsonc", "{}")

    assertEquals(root, nearestConfig(myFixture.addFileToProject("a/index.js", "").virtualFile))
  }

  fun testOverlayUsesTheManualConfig() {
    settings.state.preferOxfmtCodeStyleSettings = true
    myFixture.addFileToProject("configs/team.json", """{ "tabWidth": 6 }""")
    myFixture.addFileToProject(".oxfmtrc.json", """{ "tabWidth": 3 }""")
    useManualConfig("configs/team.json")

    val overlay = runModifier(myFixture.addFileToProject("index.js", ""))!!

    assertEquals(6, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  fun testChangingTheManualConfigRefreshesTheOverlay() {
    useRealCodeStyleLookup()
    val config = myFixture.addFileToProject("configs/team.json", """{ "tabWidth": 3 }""").virtualFile
    settings.update(settings.toData().copy(
      preferOxfmtCodeStyleSettings = true,
      configurationMode = ConfigurationMode.MANUAL,
      configPath = "configs/team.json",
    ))
    val file = myFixture.addFileToProject("index.js", "")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    waitForIndent(file, 3)

    WriteAction.runAndWait<Exception> { config.setBinaryContent("""{ "tabWidth": 5 }""".toByteArray()) }

    waitForIndent(file, 5)
  }

  fun testOverlayAppliesBothTablesWhenPreferred() {
    settings.state.preferOxfmtCodeStyleSettings = true
    myFixture.addFileToProject(".oxfmtrc.json", CONFIG)
    val file = myFixture.addFileToProject("index.js", "")

    val overlay = runModifier(file)!!

    assertEquals(7, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
    assertFalse(overlay.getCustomSettings(JSCodeStyleSettings::class.java).USE_DOUBLE_QUOTES)
  }

  fun testOverlayCoversTheFilesOwnLanguage() {
    settings.state.preferOxfmtCodeStyleSettings = true
    myFixture.addFileToProject(".oxfmtrc.json", CONFIG)
    val file = myFixture.addFileToProject("style.css", "")

    val overlay = runModifier(file)!!

    assertEquals(7, overlay.getCommonSettings(file.language).indentOptions!!.INDENT_SIZE)
  }

  fun testOverlayDoesNothingWhenNotPreferred() {
    myFixture.addFileToProject(".oxfmtrc.json", CONFIG)

    assertNull(runModifier(myFixture.addFileToProject("index.js", "")))
  }

  fun testOverlayDoesNothingForAnUnsupportedFile() {
    settings.state.preferOxfmtCodeStyleSettings = true
    myFixture.addFileToProject(".oxfmtrc.json", CONFIG)

    assertNull(runModifier(myFixture.addFileToProject("notes.txt", "")))
  }

  fun testOverlayDoesNothingWithoutAConfig() {
    settings.state.preferOxfmtCodeStyleSettings = true

    assertNull(runModifier(myFixture.addFileToProject("index.js", "")))
  }

  /** Through the platform, so this also covers that changing the file reaches an open file's settings. */
  fun testConfigChangeRefreshesTheOverlayAndLeavesTheProjectAlone() {
    useRealCodeStyleLookup()
    settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = true))
    val config = myFixture.addFileToProject(".oxfmtrc.json", """{ "tabWidth": 3 }""").virtualFile
    val file = myFixture.addFileToProject("index.js", "")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    waitForIndent(file, 3)
    val projectIndent = CodeStyle.getSettings(project).getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE

    WriteAction.runAndWait<Exception> { config.setBinaryContent("""{ "tabWidth": 5 }""".toByteArray()) }

    waitForIndent(file, 5)
    assertEquals(
      "Only the Import action changes the project's own code style",
      projectIndent,
      CodeStyle.getSettings(project).getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE,
    )
  }

  /**
   * After Import the project's settings match the config, so the overlay changes nothing. A later change
   * to the config still has to reach the open file.
   */
  fun testConfigChangeAfterImportRefreshesTheOverlay() {
    useRealCodeStyleLookup()
    settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = true))
    val config = myFixture.addFileToProject(".oxfmtrc.json", """{ "tabWidth": 3 }""").virtualFile
    val file = myFixture.addFileToProject("index.js", "")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    waitForIndent(file, 3)
    assertEquals(OxfmtImportResult.IMPORTED, runImport(file.virtualFile))
    waitForIndent(file, 3)

    WriteAction.runAndWait<Exception> { config.setBinaryContent("""{ "tabWidth": 5 }""".toByteArray()) }

    waitForIndent(file, 5)
  }

  fun testTurningThePreferenceOffRemovesTheOverlay() {
    useRealCodeStyleLookup()
    settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = true))
    myFixture.addFileToProject(".oxfmtrc.json", """{ "tabWidth": 3 }""")
    val file = myFixture.addFileToProject("index.js", "")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    waitForIndent(file, 3)

    settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = false))

    waitUntil("the overlay is gone") { CodeStyle.getIndentOptions(file).INDENT_SIZE != 3 }
  }

  fun testImportWritesToTheProjectEvenWhenThePreferenceIsOff() {
    useRealCodeStyleLookup()
    myFixture.addFileToProject(".oxfmtrc.json", CONFIG)
    val file = myFixture.addFileToProject("index.js", "").virtualFile

    assertEquals(OxfmtImportResult.IMPORTED, runImport(file))

    val manager = CodeStyleSettingsManager.getInstance(project)
    assertTrue("The change should be kept in this project", manager.USE_PER_PROJECT_SETTINGS)
    assertTrue(OxfmtCodeStyle.isApplied(CodeStyle.getSettings(project), parse(CONFIG)))
    assertFalse(
      "The IDE-wide scheme should not change",
      OxfmtCodeStyle.isApplied(CodeStyleSettingsManager.getInstance().mainProjectCodeStyle ?: CodeStyle.getDefaultSettings(), parse(CONFIG)),
    )
    assertEquals(OxfmtImportResult.ALREADY_IMPORTED, runImport(file))
  }

  fun testImportWithoutAConfig() {
    assertEquals(
      OxfmtImportResult.CONFIG_NOT_FOUND,
      runImport(myFixture.addFileToProject("index.js", "").virtualFile),
    )
  }

  private fun useManualConfig(path: String) {
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = path
  }

  /** The test framework's fixed settings would hide the modifiers. */
  private fun useRealCodeStyleLookup() {
    CodeStyle.dropTemporarySettings(project)
  }

  private fun waitForIndent(file: PsiFile, expected: Int) {
    waitUntil("the indent is $expected") { CodeStyle.getIndentOptions(file).INDENT_SIZE == expected }
  }

  /** Through [OxfmtConfig.read], which is what the overlay and Import use. */
  private fun parse(json: String): OxfmtConfig {
    val name = "config${System.nanoTime()}.json"
    val file = myFixture.addFileToProject(name, json).virtualFile
    return ReadAction.compute<OxfmtConfig, Exception> { OxfmtConfig.read(project, file)!! }
  }

  private fun nearestConfig(file: com.intellij.openapi.vfs.VirtualFile) =
    ReadAction.compute<com.intellij.openapi.vfs.VirtualFile?, Exception> { findOxfmtConfig(project, file) }

  /** Returns the changed copy, or null if the modifier left it alone. */
  private fun runModifier(file: PsiFile): TransientCodeStyleSettings? =
    ReadAction.compute<TransientCodeStyleSettings?, Exception> {
      val copy = TransientCodeStyleSettings(file.virtualFile, project, CodeStyle.getSettings(project))
      copy.takeIf { OxfmtCodeStyleSettingsModifier().modifySettings(it, file) }
    }

  private fun runImport(file: com.intellij.openapi.vfs.VirtualFile): OxfmtImportResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxfmtImportResult> {
      runBlocking { importOxfmtCodeStyle(project, file) }
    }
    return PlatformTestUtil.waitForFuture(call, 30_000)
  }
}
