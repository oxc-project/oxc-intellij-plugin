package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolFixAllOnSaveInfo
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
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

  private lateinit var context: ActionOnSaveContext

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxlintSettingsState())
    context = newContext()
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

  // The platform makes new rows each time the Actions on Save page is shown.
  fun testAnUnappliedChangeSurvivesLeavingThePageAndComingBack() {
    row().isActionOnSaveEnabled = true

    val rowAfterComingBack = row()

    assertTrue(rowAfterComingBack.isActionOnSaveEnabled)
    assertTrue(rowAfterComingBack.isModified())

    rowAfterComingBack.apply()

    assertTrue(settings.state.fixAllOnSave)
  }

  fun testUndoingAnUnappliedChangeLeavesNothingToApply() {
    row().isActionOnSaveEnabled = true
    row().isActionOnSaveEnabled = false

    assertFalse(row().isActionOnSaveEnabled)
    assertFalse(row().isModified())
  }

  fun testANewDialogDoesNotSeeAnotherDialogsUnappliedChange() {
    row().isActionOnSaveEnabled = true

    val rowInANewDialog = ToolFixAllOnSaveInfo(newContext(), OxlintDefinition)

    assertFalse(rowInANewDialog.isActionOnSaveEnabled)
    assertFalse(rowInANewDialog.isModified())
  }

  fun testAppliedRowFollowsLaterChangesToTheSetting() {
    val row = row()
    row.isActionOnSaveEnabled = true
    row.apply()

    settings.updateFixAllOnSave(false)

    assertFalse(row().isActionOnSaveEnabled)
    assertFalse(row().isModified())
  }

  fun testApplyingAnUntouchedRowKeepsTheValueStoredWhileDisabled() {
    settings.state.fixAllOnSave = true
    settings.state.configurationMode = ConfigurationMode.DISABLED

    row().apply()

    assertTrue(settings.state.fixAllOnSave)
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
      .subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { _, affectsServers -> changes += affectsServers })

    settings.updateFixAllOnSave(true)
    settings.update(settings.toData().copy(typeAware = true))

    assertEquals(listOf(false, true), changes)
  }

  private fun row(): ToolFixAllOnSaveInfo = ToolFixAllOnSaveInfo(context, OxlintDefinition)

  /** The platform only creates these for the settings dialog, and keeps one while the dialog is open. */
  private fun newContext(): ActionOnSaveContext {
    val constructor = ActionOnSaveContext::class.java.getDeclaredConstructor(
      Project::class.java, Settings::class.java, Disposable::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(project, NoPages(), testRootDisposable)
  }

  private class NoPages : Settings(emptyList()) {
    override fun selectImpl(configurable: Configurable?): Promise<in Any> = resolvedPromise()
    override fun getConfigurableWithInitializedUiComponentImpl(configurable: Configurable, initializeUiComponentIfNotYet: Boolean): Configurable? = null
    override fun checkModifiedImpl(configurable: Configurable) = Unit
    override fun setSearchText(option: String?) = Unit
  }
}
