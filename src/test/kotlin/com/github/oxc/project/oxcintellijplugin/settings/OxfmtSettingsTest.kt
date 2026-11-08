package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettingsState
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.isValidExtension
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.DEFAULT_OXFMT_EXTENSIONS
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

/** Uses the XML that older versions of the plugin and the Vite+ CLI wrote, because those files are in users' projects. */
class OxfmtSettingsTest : BasePlatformTestCase() {

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxfmtSettingsState())
  }

  fun testDefaults() {
    val state = OxfmtSettingsState()

    assertEquals(ConfigurationMode.AUTOMATIC, state.configurationMode)
    assertFalse(state.disableNestedConfig)
    assertFalse(state.fixAllOnSave)
    assertFalse(state.preferOxfmtCodeStyleSettings)
    assertEmpty(state.supportedExtensions)
  }

  fun testViteCliSampleLoads() {
    load("""
      <component name="OxfmtSettings">
        <option name="fixAllOnSave" value="true" />
        <option name="preferOxfmtCodeStyleSettings" value="true" />
      </component>
    """)

    assertTrue(settings.isFixAllOnSaveEnabled)
    assertTrue(settings.state.preferOxfmtCodeStyleSettings)
  }

  fun testStateRoundTrips() {
    load("""
      <component name="OxfmtSettings">
        <option name="binaryPath" value="/project/oxfmt" />
        <option name="configPath" value="custom.json" />
        <option name="configurationMode" value="MANUAL" />
        <option name="disableNestedConfig" value="true" />
        <option name="fixAllOnSave" value="true" />
        <option name="preferOxfmtCodeStyleSettings" value="true" />
        <option name="supportedExtensions">
          <list>
            <option value=".js" />
          </list>
        </option>
      </component>
    """)

    val saved = JDOMUtil.write(XmlSerializer.serialize(settings.state))

    for (name in listOf("binaryPath", "configPath", "configurationMode", "disableNestedConfig", "fixAllOnSave",
                        "preferOxfmtCodeStyleSettings", "supportedExtensions")) {
      assertTrue(saved, """<option name="$name"""" in saved)
    }
  }

  fun testFixOnSaveReadsFalseWhileDisabled() {
    settings.state.fixAllOnSave = true
    settings.state.configurationMode = ConfigurationMode.DISABLED

    assertFalse(settings.isFixAllOnSaveEnabled)
    assertTrue(settings.state.fixAllOnSave)
  }

  fun testEmptyExtensionsAreTheDefaults() {
    assertTrue(settings.isSupported(file("a.css")))
    assertFalse(settings.isSupported(file("a.txt")))
  }

  fun testCustomExtensionsReplaceTheDefaults() {
    settings.update(settings.toData().copy(supportedExtensions = listOf(".txt")))

    assertTrue(settings.isSupported(file("a.txt")))
    assertFalse(settings.isSupported(file("a.css")))
  }

  fun testEveryDefaultExtensionIsValidAndCanMatch() {
    for (extension in DEFAULT_OXFMT_EXTENSIONS) {
      assertTrue(extension, isValidExtension(extension))
      assertTrue(extension, settings.isSupported(file("a$extension")))
    }
  }

  fun testExtensionRuleAllowsTheDefaultsPunctuation() {
    assertTrue(isValidExtension(".sublime-build"))
    assertTrue(isValidExtension(".sublime_metrics"))
    assertFalse(isValidExtension(".a.b"))
    assertFalse(isValidExtension("js"))
    assertFalse(isValidExtension(".js x"))
  }

  fun testCodeStylePreferenceDoesNotAffectServers() {
    val changes = collectChanges()

    settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = true))
    settings.update(settings.toData().copy(disableNestedConfig = true))

    assertEquals(listOf(OxcTool.OXFMT to false, OxcTool.OXFMT to true), changes)
  }

  fun testFixOnSaveDoesNotAffectServers() {
    val changes = collectChanges()

    settings.updateFixAllOnSave(true)

    assertEquals(listOf(OxcTool.OXFMT to false), changes)
  }

  fun testPageDataDoesNotOverwriteFixOnSave() {
    settings.state.fixAllOnSave = true

    settings.update(settings.toData().copy(disableNestedConfig = true))

    assertTrue(settings.state.fixAllOnSave)
  }

  private fun collectChanges(): List<Pair<OxcTool, Boolean>> {
    val changes = mutableListOf<Pair<OxcTool, Boolean>>()
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { tool, affectsServers -> changes += tool to affectsServers })
    return changes
  }

  private fun file(name: String) = LightVirtualFile(name)

  private fun load(xml: String) {
    settings.loadState(XmlSerializer.deserialize(JDOMUtil.load(xml.trimIndent()), OxfmtSettingsState::class.java))
  }
}
