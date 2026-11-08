package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettingsState
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.frontend.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.DEFAULT_OXFMT_EXTENSIONS
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JTextField

/** Fonts differ between machines, so this is a little above what Oxlint's page uses. */
private const val MAX_PAGE_WIDTH = 640

/** The page talks to the backend over RPC, which stays in process in a test IDE. */
class OxfmtConfigurableTest : BasePlatformTestCase() {

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxfmtSettingsState())
  }

  fun testShowsTheBackendsValues() {
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = "custom.json"
    settings.state.preferOxfmtCodeStyleSettings = true
    settings.state.disableNestedConfig = true

    openPage { _, panel ->
      assertTrue(radio(panel, "Manual").isSelected)
      assertTrue(textFields(panel).any { it.text == "custom.json" })
      assertTrue(checkBox(panel, "Prefer Oxfmt").isSelected)
      assertTrue(checkBox(panel, "nested").isSelected)
    }
  }

  fun testApplyReachesTheBackend() {
    openPage { configurable, panel ->
      checkBox(panel, "Prefer Oxfmt").isSelected = true
      assertTrue(configurable.isModified)

      configurable.apply()

      waitUntil("the backend has the value") { settings.state.preferOxfmtCodeStyleSettings }
      assertFalse(configurable.isModified)
    }
  }

  fun testHasALinkInsteadOfAFixOnSaveCheckbox() {
    openPage { _, panel ->
      assertNull(UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).firstOrNull { "Save" in it.text })
      assertSize(1, UIUtil.findComponentsOfType(panel, ActionLink::class.java))
    }
  }

  fun testApplyLeavesFixOnSaveAlone() {
    settings.state.fixAllOnSave = true

    openPage { configurable, panel ->
      checkBox(panel, "nested").isSelected = true
      configurable.apply()

      waitUntil("the backend has the value") { settings.state.disableNestedConfig }
      assertTrue(settings.state.fixAllOnSave)
    }
  }

  fun testDisablingGreysOutTheRows() {
    openPage { _, panel ->
      radio(panel, "Disable").isSelected = true

      assertFalse(checkBox(panel, "nested").isEnabled)
      assertFalse(checkBox(panel, "Prefer Oxfmt").isEnabled)
    }
  }

  fun testExtensionsThatMatchTheDefaultsAreNotStored() {
    openPage { configurable, panel ->
      val extensions = textFields(panel).first { ".astro" in it.text }

      extensions.text = ".js, .ts"
      configurable.apply()
      waitUntil("the backend has the extensions") { settings.state.supportedExtensions == listOf(".js", ".ts") }

      extensions.text = DEFAULT_OXFMT_EXTENSIONS.joinToString(", ")
      configurable.apply()
      waitUntil("the backend has no extensions") { settings.state.supportedExtensions.isEmpty() }
    }
  }

  fun testApplyingOnlyTheCodeStylePreferenceDoesNotRestartServers() {
    val restarts = mutableListOf<Boolean>()
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, affectsServers -> restarts += affectsServers })

    openPage { configurable, panel ->
      checkBox(panel, "Prefer Oxfmt").isSelected = true
      configurable.apply()
      waitUntil("the backend has the value") { settings.state.preferOxfmtCodeStyleSettings }
    }

    assertEquals(listOf(false), restarts)
  }

  fun testPageFitsWithoutScrollingSideways() {
    openPage { _, panel ->
      assertTrue("Width ${panel.preferredSize.width}", panel.preferredSize.width < MAX_PAGE_WIDTH)

      radio(panel, "Manual").isSelected = true

      assertTrue("Width in Manual mode ${panel.preferredSize.width}", panel.preferredSize.width < MAX_PAGE_WIDTH)
    }
  }

  private fun openPage(check: (OxfmtConfigurable, JComponent) -> Unit) {
    val configurable = OxfmtConfigurable(project)
    try {
      val panel = configurable.createComponent()
      configurable.reset()
      assertFalse("Nothing can be applied before the backend answers", configurable.isModified)
      waitUntil("the page has loaded") { configurable.isLoaded }
      check(configurable, panel)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  private fun radio(panel: JComponent, text: String): JBRadioButton =
    UIUtil.findComponentsOfType(panel, JBRadioButton::class.java).first { text in it.text }

  private fun checkBox(panel: JComponent, text: String): JBCheckBox =
    UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).first { text in it.text }

  private fun textFields(panel: JComponent): List<JTextField> =
    UIUtil.findComponentsOfType(panel, JTextField::class.java)
}
