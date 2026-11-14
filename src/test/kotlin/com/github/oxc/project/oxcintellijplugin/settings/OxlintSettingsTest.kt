package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsState
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.github.oxc.project.oxcintellijplugin.testutil.resetOxcSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer

/** Uses the XML that older versions of the plugin wrote, because those files are in users' projects. */
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
    }
    finally {
      super.tearDown()
    }
  }

  fun testDefaults() {
    val state = OxlintSettingsState()

    assertEquals(ConfigurationMode.AUTOMATIC, state.configurationMode)
    assertEquals(OxlintFixKind.SAFE_FIX, state.fixKind)
    assertEquals(OxlintUnusedDisableDirectives.ALLOW, state.unusedDisableDirectives)
    assertFalse(state.typeAware)
    assertFalse(state.disableNestedConfig)
  }

  fun testManualSampleLoadsUnchanged() {
    load("""
      <component name="OxcSettings">
        <option name="binaryPath" value="/project/node_modules/oxlint/bin/oxlint" />
        <option name="configPath" value="/project/custom-oxlint.jsonc" />
        <option name="configurationMode" value="MANUAL" />
      </component>
    """)

    assertEquals(ConfigurationMode.MANUAL, settings.state.configurationMode)
    assertEquals("/project/node_modules/oxlint/bin/oxlint", settings.state.binaryPath)
    assertEquals("/project/custom-oxlint.jsonc", settings.state.configPath)
  }

  /** The layout main writes, from `@get:XMap` on its `flags` field. */
  fun testMainsFileIsReadAndSavedTheSameWay() {
    load("""
      <component name="OxcSettings">
        <option name="fixAllOnSave" value="true" />
        <flags>
          <entry key="fix_kind" value="DANGEROUS_FIX" />
          <entry key="disable_nested_config" value="true" />
        </flags>
        <option name="typeAware" value="true" />
        <option name="unusedDisableDirectives" value="WARN" />
      </component>
    """)

    assertEquals(OxlintFixKind.DANGEROUS_FIX, settings.state.fixKind)
    assertTrue(settings.state.disableNestedConfig)
    assertTrue(settings.state.fixAllOnSave)
    assertTrue(settings.state.typeAware)
    assertEquals(OxlintUnusedDisableDirectives.WARN, settings.state.unusedDisableDirectives)

    val saved = saved()
    assertFalse(saved, "fixKind" in saved || "disableNestedConfig" in saved)
    assertTrue(saved, "<flags>" in saved)
    assertFalse(saved, """<option name="flags">""" in saved)
    assertTrue(saved, """<entry key="fix_kind" value="DANGEROUS_FIX" />""" in saved)
    assertTrue(saved, """<entry key="disable_nested_config" value="true" />""" in saved)
  }

  fun testLowercaseLegacyFixKindIsRead() {
    load("""
      <component name="OxcSettings">
        <flags><entry key="fix_kind" value="safe_fix_or_suggestion" /></flags>
      </component>
    """)

    assertEquals(OxlintFixKind.SAFE_FIX_OR_SUGGESTION, settings.state.fixKind)
    assertEquals(mapOf("fix_kind" to "SAFE_FIX_OR_SUGGESTION"), settings.state.flags)
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

  fun testOtherFlagsAreKept() {
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

    assertEquals(mapOf("disable_nested_config" to "true", "fix_kind" to "ALL"), settings.state.flags)

    settings.update(settings.toData().copy(disableNestedConfig = false, fixKind = OxlintFixKind.SAFE_FIX))

    assertEquals(emptyMap<String, String>(), settings.state.flags)
    assertFalse(saved(), "flags" in saved())
  }

  fun testOnlyTypedValuesReachTheServer() {
    load("""
      <component name="OxcSettings">
        <flags><entry key="fix_kind" value="DANGEROUS_FIX" /></flags>
      </component>
    """)

    val config = OxlintDefinition.workspaceConfig(project, null)

    assertFalse(config.toString(), "flags" in config)
    assertEquals("dangerous_fix", config["fixKind"])
  }

  fun testSavedSettingsLoadBack() {
    val state = OxlintSettingsState().apply {
      configurationMode = ConfigurationMode.MANUAL
      configPath = "custom.json"
      fixKind = OxlintFixKind.ALL
      supportedExtensions = mutableListOf(".js")
      unusedDisableDirectives = OxlintUnusedDisableDirectives.DENY
    }

    val loaded = XmlSerializer.deserialize(XmlSerializer.serialize(state), OxlintSettingsState::class.java)

    assertEquals(state, loaded)
  }

  fun testAnEmptyExtensionListMeansTheDefaultsAndOtherListsReplaceThem() {
    assertTrue(settings.isSupported(LightVirtualFile("a.ts")))
    assertFalse(settings.isSupported(LightVirtualFile("a.txt")))

    settings.state.supportedExtensions = mutableListOf(".txt")

    assertTrue(settings.isSupported(LightVirtualFile("a.txt")))
    assertFalse(settings.isSupported(LightVirtualFile("a.ts")))
    assertFalse(settings.isSupported(LightVirtualFile("noExtension")))
  }

  fun testLoadingDifferentStateNotifiesAndLoadingTheSameStateDoesNot() {
    var changes = 0
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })
    settings.loadState(OxlintSettingsState())
    changes = 0

    settings.loadState(OxlintSettingsState())
    assertEquals(0, changes)

    settings.loadState(OxlintSettingsState().apply { typeAware = true })
    assertEquals(1, changes)
  }

  fun testUpdateChangesTheStateInPlaceSoThatItGetsSaved() {
    var changes = 0
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })
    val stateBefore = settings.state
    val countBefore = stateBefore.modificationCount

    settings.update(settings.toData())
    assertEquals(0, changes)
    assertEquals(countBefore, stateBefore.modificationCount)

    settings.update(settings.toData().copy(configPath = "custom.json", fixKind = OxlintFixKind.ALL))
    assertEquals(1, changes)
    assertSame(stateBefore, settings.state)
    assertTrue(stateBefore.modificationCount > countBefore)
    assertEquals("custom.json", settings.state.configPath)
    assertEquals(OxlintFixKind.ALL, settings.state.fixKind)

    settings.update(settings.toData().copy(configPath = "  "))
    assertNull(settings.state.configPath)
  }

  fun testUpdateKeepsWhatThePageDoesNotEdit() {
    settings.state.fixAllOnSave = true

    settings.update(settings.toData().copy(typeAware = true))

    assertTrue(settings.state.fixAllOnSave)
  }

  private fun saved(): String = JDOMUtil.write(XmlSerializer.serialize(settings.state))

  private fun load(xml: String) {
    settings.loadState(XmlSerializer.deserialize(JDOMUtil.load(xml), OxlintSettingsState::class.java))
  }
}
