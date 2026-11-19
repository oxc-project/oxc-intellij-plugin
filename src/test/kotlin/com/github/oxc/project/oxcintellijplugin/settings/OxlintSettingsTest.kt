package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsState
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.github.oxc.project.oxcintellijplugin.testutil.resetOxcSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

class OxlintSettingsTest : BasePlatformTestCase() {

  private val settings: OxlintSettings
    get() = OxlintSettings.getInstance(project)

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxlintSettingsState())
  }

  override fun tearDown() {
    try {
      resetOxcSettings(project)
    } finally {
      super.tearDown()
    }
  }

  fun testDefaultsMatchExpectedValue() {
    val state = OxlintSettingsState()

    assertEquals(ConfigurationMode.AUTOMATIC, state.configurationMode)
    assertEquals(OxlintFixKind.SAFE_FIX, state.fixKind)
    assertEquals(OxlintUnusedDisableDirectives.ALLOW, state.unusedDisableDirectives)
    assertFalse(state.typeAware)
    assertFalse(state.disableNestedConfig)
  }

  fun testManualConfigLoadsWithExpectedValues() {
    val binaryPath = "/project/node_modules/oxlint/bin/oxlint"
    val configPath = "/project/custom-oxlint.jsonc"

    load("""
      <component name="OxcSettings">
        <option name="binaryPath" value="$binaryPath" />
        <option name="configPath" value="$configPath" />
        <option name="configurationMode" value="MANUAL" />
      </component>
    """)

    assertEquals(ConfigurationMode.MANUAL, settings.state.configurationMode)
    assertEquals(binaryPath, settings.state.binaryPath)
    assertEquals(configPath, settings.state.configPath)
  }

  fun testFileIsReadAndSavedTheSameWay() {
    val disableNestedConfig = true
    val fixAllOnSave = true
    val fixKind = OxlintFixKind.DANGEROUS_FIX
    val typeAware = true
    val unusedDisableDirectives = OxlintUnusedDisableDirectives.WARN

    load("""
      <component name="OxcSettings">
        <option name="fixAllOnSave" value="$fixAllOnSave" />
        <flags>
          <entry key="fix_kind" value="$fixKind" />
          <entry key="disable_nested_config" value="$disableNestedConfig" />
        </flags>
        <option name="typeAware" value="$typeAware" />
        <option name="unusedDisableDirectives" value="$unusedDisableDirectives" />
      </component>
    """)

    assertEquals(fixKind, settings.state.fixKind)
    assertEquals(disableNestedConfig, settings.state.disableNestedConfig)
    assertEquals(fixAllOnSave, settings.state.fixAllOnSave)
    assertEquals(typeAware, settings.state.typeAware)
    assertEquals(unusedDisableDirectives, settings.state.unusedDisableDirectives)

    val saved = saved()
    assertFalse("fixKind" in saved)
    assertFalse("disableNestedConfig" in saved)
    assertFalse("""<option name="flags">""" in saved)

    assertTrue("""<option name="fixAllOnSave" value="$fixAllOnSave" />""" in saved)
    assertTrue("<flags>" in saved)
    assertTrue("""<entry key="fix_kind" value="$fixKind" />""" in saved)
    assertTrue("""<entry key="disable_nested_config" value="$disableNestedConfig" />""" in saved)
    assertTrue("""<option name="typeAware" value="$typeAware" />""" in saved)
    assertTrue("""<option name="unusedDisableDirectives" value="$unusedDisableDirectives" />""" in saved)
  }

  fun testLowercaseLegacyFixKindIsRead() {
    val fixKind = OxlintFixKind.SAFE_FIX_OR_SUGGESTION
    load("""
      <component name="OxcSettings">
        <flags><entry key="fix_kind" value="${fixKind.serverValue}" /></flags>
      </component>
    """)

    assertEquals(fixKind, settings.state.fixKind)
    assertEquals(mapOf("fix_kind" to fixKind.name), settings.state.flags)
  }

  fun testUnknownLegacyFixKindFallsBackToTheDefault() {
    load("""
      <component name="OxcSettings">
        <flags><entry key="fix_kind" value="NOT_A_KIND" /></flags>
      </component>
    """)

    assertEquals(OxlintFixKind.SAFE_FIX, settings.state.fixKind)
    assertEquals(emptyMap<String, String>(), settings.state.flags)
  }

  fun testUnknownFlagsAreKeptUnmodified() {
    load("""
      <component name="OxcSettings">
        <flags><entry key="something_else" value="1" /></flags>
      </component>
    """)

    settings.update(settings.toData().copy(fixKind = OxlintFixKind.NONE))

    assertEquals(mapOf("something_else" to "1", "fix_kind" to "NONE"), settings.state.flags)
  }

  fun testUpdateWritesTheFlags() {
    settings.update(settings.toData().copy(disableNestedConfig = true, fixKind = OxlintFixKind.ALL))

    assertEquals(mapOf("disable_nested_config" to "true", "fix_kind" to "ALL"),
      settings.state.flags)

    settings.update(
      settings.toData().copy(disableNestedConfig = false, fixKind = OxlintFixKind.SAFE_FIX))

    assertEquals(emptyMap<String, String>(), settings.state.flags)
    assertFalse(saved(), "flags" in saved())
  }

  fun testSerializationRoundTripMatches() {
    val state = OxlintSettingsState().apply {
      configurationMode = ConfigurationMode.MANUAL
      configPath = "custom.json"
      fixKind = OxlintFixKind.ALL
      supportedExtensions = mutableListOf(".js")
      unusedDisableDirectives = OxlintUnusedDisableDirectives.DENY
    }

    val loaded = XmlSerializer.deserialize(XmlSerializer.serialize(state),
      OxlintSettingsState::class.java)

    assertEquals(state, loaded)
  }

  fun testAnEmptyExtensionListMeansTheDefaults() {
    assertTrue(settings.isSupported(LightVirtualFile("a.ts")))
    assertFalse(settings.isSupported(LightVirtualFile("a.txt")))
  }

  fun testCustomExtensionList() {
    settings.state.supportedExtensions = mutableListOf(".txt")

    assertTrue(settings.isSupported(LightVirtualFile("a.txt")))
    assertFalse(settings.isSupported(LightVirtualFile("a.ts")))
    assertFalse(settings.isSupported(LightVirtualFile("noExtension")))
  }

  fun testLoadingDifferentStateNotifies() {
    var changes = 0
    settings.loadState(OxlintSettingsState())
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })

    settings.loadState(OxlintSettingsState().apply { typeAware = true })
    assertEquals(1, changes)
  }

  fun testLoadingSameStateDoesNotNotify() {
    var changes = 0
    settings.loadState(OxlintSettingsState())
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })
    settings.loadState(OxlintSettingsState())

    assertEquals(0, changes)
  }

  fun testUpdateChangesTheStateInPlaceSoThatItGetsSaved() {
    var changes = 0
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })
    val stateBefore = settings.state
    val countBefore = stateBefore.modificationCount

    settings.update(settings.toData().copy(configPath = "custom.json", fixKind = OxlintFixKind.ALL))
    assertEquals(1, changes)
    assertSame(stateBefore, settings.state)
    assertTrue(stateBefore.modificationCount > countBefore)
    assertEquals("custom.json", settings.state.configPath)
    assertEquals(OxlintFixKind.ALL, settings.state.fixKind)
  }

  fun testUnchangedUpdateDoesNotNotifyOrModify() {
    var changes = 0
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })
    val countBefore = settings.state.modificationCount

    settings.update(settings.toData())

    assertEquals(0, changes)
    assertEquals(countBefore, settings.state.modificationCount)
  }

  fun testBlankConfigPathIsStoredAsUnset() {
    settings.update(settings.toData().copy(configPath = "custom.json"))

    settings.update(settings.toData().copy(configPath = "  "))

    assertNull(settings.state.configPath)
  }

  fun testChangesDoNotUpdateUnmodifiedSettings() {
    settings.state.fixAllOnSave = true

    settings.update(settings.toData().copy(typeAware = true))

    assertTrue(settings.state.fixAllOnSave)
  }

  private fun saved(): String = JDOMUtil.write(XmlSerializer.serialize(settings.state))

  private fun load(xml: String) {
    settings.loadState(
      XmlSerializer.deserialize(JDOMUtil.load(xml), OxlintSettingsState::class.java))
  }
}
