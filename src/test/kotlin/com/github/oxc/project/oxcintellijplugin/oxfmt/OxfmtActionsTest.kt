package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettingsState
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolFixAllOnSaveInfo
import com.github.oxc.project.oxcintellijplugin.frontend.settings.OxcSettingsCache
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.CopyOnWriteArrayList

/** The shared code behind these is covered through Oxlint, so this checks what's particular to Oxfmt. */
class OxfmtActionsTest : BasePlatformTestCase() {

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxfmtSettingsState())
    settings.update(settings.toData().copy(disableNestedConfig = true))
    // The whole settings, because a single field can already have the value from the test before, which
    // shares this project, while the rest still lags behind.
    val expected = settings.toData()
    waitUntil("the frontend has the settings") { project.service<OxcSettingsCache>().oxfmt == expected }
  }

  fun testFixAllIsShownForAFileOnlyOxfmtHandles() {
    assertTrue(update("OxfmtApplyFixAllAction", file("a.css")).isVisible)
    assertFalse(update("OxlintApplyFixAllAction", file("b.css")).isVisible)
  }

  fun testFixAllIsHiddenForAnUnsupportedFile() {
    assertFalse(update("OxfmtApplyFixAllAction", file("a.txt")).isVisible)
  }

  fun testImportIsShownForSupportedFilesAndFolders() {
    val file = file("a/b.js")

    assertTrue(update("OxfmtImportCodeStyleAction", file).isVisible)
    assertTrue(update("OxfmtImportCodeStyleAction", file.parent).isVisible)
    assertFalse(update("OxfmtImportCodeStyleAction", file("c.txt")).isVisible)
  }

  fun testActionsAreHiddenWhenDisabled() {
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.DISABLED))
    waitUntil("the frontend knows") {
      project.service<OxcSettingsCache>().oxfmt?.configurationMode == ConfigurationMode.DISABLED
    }
    val file = file("a.js")

    assertFalse(update("OxfmtApplyFixAllAction", file).isVisible)
    assertFalse(update("OxfmtImportCodeStyleAction", file).isVisible)
  }

  fun testImportSaysWhenThereIsNoConfig() {
    val notifications = CopyOnWriteArrayList<Notification>()
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "Oxc") notifications += notification
      }
    })
    val file = file("a.js")

    ActionManager.getInstance().getAction("OxfmtImportCodeStyleAction")
      .actionPerformed(event(file, Presentation()))

    waitUntil("the notification appears") { notifications.isNotEmpty() }
    assertEquals("Oxfmt configuration file not found", notifications.single().title)
  }

  fun testOnSaveRowEditsOxfmtsOwnSetting() {
    val row = ToolFixAllOnSaveInfo(context(), OxfmtDefinition)
    assertEquals("Run Oxfmt 'Fix All' on Save", row.actionOnSaveName)

    row.isActionOnSaveEnabled = true
    row.apply()

    assertTrue(settings.state.fixAllOnSave)
  }

  /** The platform only creates these for the settings dialog. */
  private fun context(): ActionOnSaveContext {
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

  private fun update(id: String, file: VirtualFile): Presentation {
    val presentation = Presentation()
    ActionManager.getInstance().getAction(id).update(event(file, presentation))
    return presentation
  }

  private fun event(file: VirtualFile, presentation: Presentation): AnActionEvent =
    AnActionEvent.createEvent(
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.VIRTUAL_FILE, file)
        .build(),
      presentation,
      ActionPlaces.EDITOR_POPUP,
      ActionUiKind.POPUP,
      null,
    )

  private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "").virtualFile
}
