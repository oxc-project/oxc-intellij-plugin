package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.frontend.tool.fixOnSaveFailureNotification
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixOnSaveFailure
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.notification.NotificationType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FixOnSaveFailureNotificationTest : BasePlatformTestCase() {

  /** A missing key shows as `!key!`. */
  fun testEveryFailureForEveryToolHasItsMessages() {
    for (tool in OxcTool.entries) for (result in listOf(OxcFixAllResult.TimedOut, OxcFixAllResult.Failed("the server failed"))) {
      val notification = fixOnSaveFailureNotification(OxcFixOnSaveFailure(tool, "a.js", result))!!

      assertEquals(NotificationType.ERROR, notification.type)
      for (text in listOf(notification.title, notification.content)) {
        assertFalse("$tool, $result: \"$text\"", text.isBlank() || text.startsWith("!"))
      }
      assertTrue(notification.content, "a.js" in notification.content)
    }
  }

  fun testServerMessageIsShownAsText() {
    val failure = OxcFixOnSaveFailure(OxcTool.OXFMT, "<a>.js", OxcFixAllResult.Failed("expected <b>"))

    val content = fixOnSaveFailureNotification(failure)!!.content

    assertTrue(content, "&lt;a&gt;.js" in content && "expected &lt;b&gt;" in content)
  }

  fun testResultThatIsNotAFailureShowsNothing() {
    assertNull(fixOnSaveFailureNotification(OxcFixOnSaveFailure(OxcTool.OXFMT, "a.js", OxcFixAllResult.Applied)))
  }
}
