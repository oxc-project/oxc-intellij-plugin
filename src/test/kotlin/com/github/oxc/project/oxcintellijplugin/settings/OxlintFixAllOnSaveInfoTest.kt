package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix.OxlintFixAllOnSaveInfo
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsListener
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsState
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

class OxlintFixAllOnSaveInfoTest : BasePlatformTestCase() {

  private val settings: OxlintSettings
    get() = OxlintSettings.getInstance(project)

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxlintSettingsState())
  }

  fun testApplyStoresTheValue() {
    val row = row()
    assertFalse(row.isActionOnSaveEnabled)

    row.isActionOnSaveEnabled = true
    assertTrue(row.isModified())
    assertFalse("Only apply should change the setting", settings.state.fixAllOnSave)

    row.apply()

    assertTrue(settings.state.fixAllOnSave)
    assertFalse(row.isModified())
  }

  fun testUnavailableWhileDisabled() {
    settings.state.fixAllOnSave = true
    settings.state.configurationMode = ConfigurationMode.DISABLED

    val row = row()

    assertFalse(row.isSaveActionApplicable)
    assertFalse(row.isActionOnSaveEnabled)
    assertEquals("Oxlint integration is disabled.", row.comment.commentText)
  }

  fun testAvailableShowsWhatItRuns() {
    assertEquals("Oxlint 'Fix All'", row().comment.commentText)
    assertEquals("Run Oxlint 'Fix All' on Save", row().actionOnSaveName)
  }

  fun testHasNoLinkWhenTheOxlintPageIsNotInTheDialog() {
    assertEmpty(row().actionLinks)
  }

  fun testChangingOnlyFixOnSaveDoesNotAffectServers() {
    val changes = mutableListOf<Boolean>()
    project.messageBus.connect(testRootDisposable)
      .subscribe(OxlintSettingsListener.TOPIC, OxlintSettingsListener { changes += it })

    settings.update(settings.toData().copy(fixAllOnSave = true))
    settings.update(settings.toData().copy(typeAware = true))

    assertEquals(listOf(false, true), changes)
  }

  /** The platform only creates these for the settings dialog. */
  private fun row(): OxlintFixAllOnSaveInfo {
    val constructor = ActionOnSaveContext::class.java.getDeclaredConstructor(
      Project::class.java, Settings::class.java, Disposable::class.java)
    constructor.isAccessible = true
    return OxlintFixAllOnSaveInfo(constructor.newInstance(project, NoPages(), testRootDisposable))
  }

  private class NoPages : Settings(emptyList()) {
    override fun selectImpl(configurable: Configurable?): Promise<in Any> = resolvedPromise()
    override fun getConfigurableWithInitializedUiComponentImpl(configurable: Configurable, initializeUiComponentIfNotYet: Boolean): Configurable? = null
    override fun checkModifiedImpl(configurable: Configurable) = Unit
    override fun setSearchText(option: String?) = Unit
  }
}
