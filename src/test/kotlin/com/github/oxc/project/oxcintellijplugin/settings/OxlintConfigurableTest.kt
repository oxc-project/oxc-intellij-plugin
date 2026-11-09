package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsState
import com.github.oxc.project.oxcintellijplugin.frontend.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.frontend.settings.findInvalidExtension
import com.github.oxc.project.oxcintellijplugin.frontend.settings.parseExtensions
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.DEFAULT_OXLINT_EXTENSIONS
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.github.oxc.project.oxcintellijplugin.testutil.resetOxcSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.EDT
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.UIUtil
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField

/** The page talks to the backend over RPC, which stays in process in a test IDE. */
/** Fonts differ between machines, so this is a little above the 621 seen on Linux with the 45 column comment. */
private const val MAX_PAGE_WIDTH = 640

class OxlintConfigurableTest : BasePlatformTestCase() {

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

  fun testIsNotModifiableUntilTheBackendAnswersThenShowsItsValues() {
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = "custom.json"
    settings.state.fixKind = OxlintFixKind.ALL

    openPage { configurable, panel ->
      assertFalse(configurable.isModified)
      assertTrue(radio(panel, "Manual").isSelected)
      assertContainsElements(textFields(panel).map { it.text }, "custom.json")
      assertEquals(OxlintFixKind.ALL, combo(panel, OxlintFixKind.ALL).selectedItem)

      checkBox(panel, "type aware").doClick()

      assertTrue(configurable.isModified)
      assertFalse("Only apply should change the settings", settings.state.typeAware)
    }
  }

  fun testApplyStoresEditsOnTheBackendAndNotifies() {
    var changes = 0
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, _ -> changes++ })

    openPage { configurable, panel ->
      radio(panel, "Manual").doClick()
      checkBox(panel, "type aware").doClick()
      checkBox(panel, "nested").doClick()
      combo(panel, OxlintFixKind.SAFE_FIX).selectedItem = OxlintFixKind.DANGEROUS_FIX
      configurable.apply()

      waitUntil("the backend has the settings") { changes == 1 }
      assertEquals(ConfigurationMode.MANUAL, settings.state.configurationMode)
      assertTrue(settings.state.typeAware)
      assertTrue(settings.state.disableNestedConfig)
      assertEquals(OxlintFixKind.DANGEROUS_FIX, settings.state.fixKind)
      assertFalse(configurable.isModified)
    }
  }

  /**
   * Settings opened from a modal dialog is modal too. The page must still load, because the platform calls
   * reset before the page is in a window, where its own modality would be the non-modal one.
   */
  fun testLoadsWhileAModalDialogIsShowing() {
    val configurable = OxlintConfigurable(project)
    try {
      configurable.createComponent()
      runWithModalProgressBlocking(project, "Modal dialog") {
        withContext(Dispatchers.EDT) { configurable.reset() }
        withTimeoutOrNull(20.seconds) { while (!configurable.isLoaded) delay(20) }
      }
      assertTrue("The page should load while the modal dialog shows", configurable.isLoaded)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  fun testFixOnSaveIsOnlyEditedOnTheActionsOnSavePage() {
    openPage { _, panel ->
      assertEmpty(UIUtil.findComponentsOfType(panel, JBCheckBox::class.java).filter { "Fix All" in it.text })
      assertSize(1, UIUtil.findComponentsOfType(panel, ActionLink::class.java).filter { "actions on save" in it.text.lowercase() })
    }
  }

  fun testAPageOpenedBeforeFixOnSaveChangedDoesNotUndoIt() {
    openPage { configurable, panel ->
      settings.updateFixAllOnSave(true)

      combo(panel, OxlintFixKind.SAFE_FIX).selectedItem = OxlintFixKind.ALL
      configurable.apply()

      waitUntil("the backend has the settings") { settings.state.fixKind == OxlintFixKind.ALL }
      assertTrue(settings.state.fixAllOnSave)
    }
  }

  fun testDisablingKeepsTheStoredFixOnSaveValue() {
    settings.state.fixAllOnSave = true

    openPage { configurable, panel ->
      radio(panel, "Disable").doClick()
      configurable.apply()

      waitUntil("the backend has the settings") { settings.state.configurationMode == ConfigurationMode.DISABLED }
      assertTrue(settings.state.fixAllOnSave)
      assertFalse("Nothing is left to apply", configurable.isModified)
    }
  }

  fun testEnablingKeepsTheStoredFixOnSaveValue() {
    settings.state.fixAllOnSave = true
    settings.state.configurationMode = ConfigurationMode.DISABLED

    openPage { configurable, panel ->
      radio(panel, "Automatic").doClick()
      configurable.apply()

      waitUntil("the backend has the settings") { settings.state.configurationMode == ConfigurationMode.AUTOMATIC }
      assertTrue(settings.state.fixAllOnSave)
      assertFalse("Nothing is left to apply", configurable.isModified)
    }
  }

  fun testPageFitsWithoutScrollingSideways() {
    openPage { _, panel ->
      assertTrue("Width ${panel.preferredSize.width}", panel.preferredSize.width < MAX_PAGE_WIDTH)

      // Shows the path rows, whose longer labels make the page wider.
      radio(panel, "Manual").doClick()
      panel.doLayout()

      assertTrue("Width in Manual mode ${panel.preferredSize.width}", panel.preferredSize.width < MAX_PAGE_WIDTH)
    }
  }

  fun testResetShowsWhatChangedOnTheBackend() {
    openPage { configurable, panel ->
      settings.state.typeAware = true

      configurable.reset()
      waitUntil("the page has loaded") { configurable.isLoaded }

      assertTrue(checkBox(panel, "type aware").isSelected)
    }
  }

  fun testExtensionsThatMatchTheDefaultsAreNotStored() {
    openPage { configurable, panel ->
      val extensions = textFields(panel).first { ".astro" in it.text }

      extensions.text = ".js, .ts"
      configurable.apply()
      waitUntil("the backend has the extensions") { settings.state.supportedExtensions == listOf(".js", ".ts") }
      assertFalse("Nothing is left to apply", configurable.isModified)

      extensions.text = DEFAULT_OXLINT_EXTENSIONS.joinToString(", ")
      configurable.apply()
      waitUntil("the backend has no extensions") { settings.state.supportedExtensions.isEmpty() }
    }
  }

  /** The plugin has no help pages, so a help topic would only give the pages a "?" that goes nowhere. */
  fun testPagesHaveNoHelpTopic() {
    assertNull(OxlintConfigurable(project).helpTopic)
    assertNull(OxfmtConfigurable(project).helpTopic)
  }

  fun testExtensionParsingAndValidation() {
    assertEquals(listOf(".js", ".ts"), parseExtensions(" .js , .ts ,, "))
    assertNull(findInvalidExtension(".js, .TS2"))
    assertEquals("js", findInvalidExtension(".ts, js"))
    assertEquals(".a.b", findInvalidExtension(".a.b"))
    assertEquals("", findInvalidExtension(".js,"))
  }

  private fun openPage(check: (OxlintConfigurable, JComponent) -> Unit) {
    val configurable = OxlintConfigurable(project)
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

  private fun combo(panel: JComponent, item: Any): JComboBox<*> =
    UIUtil.findComponentsOfType(panel, JComboBox::class.java).first { combo ->
      (0 until combo.itemCount).any { combo.getItemAt(it) == item }
    }
}
