package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix.OxlintFixAllOnSaveAction
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsState
import com.github.oxc.project.oxcintellijplugin.frontend.settings.OxcSettingsCache
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList

class FixAllActionTest : BasePlatformTestCase() {

  private val settings: OxlintSettings
    get() = OxlintSettings.getInstance(project)

  // Light test projects are shared between tests, and so are their settings.
  override fun setUp() {
    super.setUp()
    settings.loadState(OxlintSettingsState())
    settings.update(settings.toData().copy(typeAware = true))
    waitForCache()
  }

  override fun tearDown() {
    try {
      settings.loadState(OxlintSettingsState())
    }
    finally {
      super.tearDown()
    }
  }

  fun testRunningTheActionWithNothingToFixSaysSo() {
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.DISABLED))

    val notification = runAction()

    assertEquals("No fixes to apply", notification.title)
    assertEquals("Oxlint has no fixes to apply to \"a.js\".", notification.content)
    assertEquals(NotificationType.INFORMATION, notification.type)
  }

  fun testRunningTheActionWithFixKindNoneSaysFixesAreOff() {
    settings.update(settings.toData().copy(fixKind = OxlintFixKind.NONE))

    val notification = runAction()

    assertEquals("Oxlint fixes are turned off", notification.title)
    assertEquals("The Oxlint Fix Kind setting is None, so no fixes were applied.", notification.content)
    assertEquals(NotificationType.INFORMATION, notification.type)
    assertEquals(listOf("Open Oxlint settings"), notification.actions.map { it.templateText })
  }

  fun testRunningTheActionWithoutOxlintSaysItWasNotFound() {
    val notification = runAction()

    assertEquals("Oxlint not found", notification.title)
    assertEquals(
      "Couldn't find the oxlint package for \"a.js\". Install it in the project, or set the path to it in the Oxlint settings.",
      notification.content,
    )
    assertEquals(NotificationType.WARNING, notification.type)
    assertEquals(listOf("Open Oxlint settings"), notification.actions.map { it.templateText })
  }

  fun testSavingWithNothingToFixStaysQuiet() {
    val notifications = collectNotifications()
    settings.update(settings.toData().copy(fixKind = OxlintFixKind.NONE))
    settings.updateFixAllOnSave(true)
    val document = FileDocumentManager.getInstance().getDocument(file("a.js"))!!
    val call = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking { OxlintFixAllOnSaveAction().updateDocument(project, document) }
    }

    PlatformTestUtil.waitForFuture(call, 30_000)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEmpty(notifications)
  }

  fun testVisibleAndEnabledForASupportedFile() {
    val presentation = update(file("a.js"))

    assertTrue(presentation.isVisible)
    assertTrue(presentation.isEnabled)
  }

  fun testHiddenInAMenuForAnUnsupportedFile() {
    val presentation = update(file("a.txt"))

    assertFalse(presentation.isVisible)
    assertFalse(presentation.isEnabled)
  }

  fun testHiddenInAMenuWhenDisabled() {
    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.DISABLED))
    waitUntil("the frontend knows") {
      project.service<OxcSettingsCache>().oxlint?.configurationMode == ConfigurationMode.DISABLED
    }

    assertFalse(update(file("a.js")).isVisible)
  }

  fun testFollowsCustomExtensions() {
    settings.update(settings.toData().copy(supportedExtensions = listOf(".txt")))
    waitUntil("the frontend knows") {
      project.service<OxcSettingsCache>().oxlint?.supportedExtensions == listOf(".txt")
    }

    assertTrue(update(file("a.txt")).isVisible)
    assertFalse(update(file("a.js")).isVisible)
  }

  fun testOnlyDisabledOutsideAMenu() {
    val presentation = Presentation()
    action().update(event(file("a.txt"), presentation, ActionPlaces.UNKNOWN, ActionUiKind.NONE))

    assertTrue(presentation.isVisible)
    assertFalse(presentation.isEnabled)
  }

  private fun runAction(): Notification {
    val notifications = collectNotifications()
    val file = file("a.js")

    action().actionPerformed(event(file, Presentation(), ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP))

    waitUntil("the notification appears") { notifications.isNotEmpty() }
    return notifications.single()
  }

  private fun collectNotifications(): List<Notification> {
    val notifications = CopyOnWriteArrayList<Notification>()
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "Oxc") notifications += notification
      }
    })
    return notifications
  }

  private fun waitForCache() {
    waitUntil("the frontend has the settings") { project.service<OxcSettingsCache>().oxlint?.typeAware == true }
  }

  private fun update(file: VirtualFile): Presentation {
    val presentation = Presentation()
    action().update(event(file, presentation, ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP))
    return presentation
  }

  private fun event(file: VirtualFile, presentation: Presentation, place: String, kind: ActionUiKind): AnActionEvent =
    AnActionEvent.createEvent(
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.VIRTUAL_FILE, file)
        .build(),
      presentation,
      place,
      kind,
      null,
    )

  private fun action() = ActionManager.getInstance().getAction("OxlintApplyFixAllAction")

  private fun file(name: String): VirtualFile = myFixture.addFileToProject(name, "").virtualFile
}
