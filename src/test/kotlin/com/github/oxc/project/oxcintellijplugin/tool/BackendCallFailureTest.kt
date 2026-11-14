package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.frontend.callBackendReportingFailure
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Restart, Import and the config balloons' actions call the backend from a coroutine. An exception there
 * would only reach the IDE's error reporter, which the test logger turns into a failure.
 */
class BackendCallFailureTest : BasePlatformTestCase() {

  fun testABackendFailureIsShownAsABalloon() {
    val notifications = collectNotifications()

    val result = runBlocking {
      callBackendReportingFailure<Unit>(project, "oxfmt.import.code.style.failed") {
        throw IllegalStateException("The config went away")
      }
    }

    assertNull(result)
    waitUntil("the notification appears") { notifications.isNotEmpty() }
    val notification = notifications.single()
    assertEquals("Couldn't import the Oxfmt code style", notification.title)
    assertEquals("The config went away", notification.content)
    assertEquals(NotificationType.ERROR, notification.type)
  }

  fun testASuccessfulCallReturnsItsResult() {
    val notifications = collectNotifications()

    assertEquals("done", runBlocking { callBackendReportingFailure(project, "oxfmt.import.code.style.failed") { "done" } })
    assertEmpty(notifications)
  }

  fun testCancellationIsPassedOnRatherThanShown() {
    val notifications = collectNotifications()

    assertThrows(CancellationException::class.java) {
      runBlocking {
        callBackendReportingFailure<Unit>(project, "oxfmt.import.code.style.failed") { throw CancellationException("stopped") }
      }
    }
    assertEmpty(notifications)
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
}
