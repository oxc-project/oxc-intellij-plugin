package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.frontend.tool.fixAllNotification
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FixAllNotificationTest : BasePlatformTestCase() {

  /** A missing key shows as `!key!`, so a message the balloon can build for no tool goes unnoticed until then. */
  fun testEveryBalloonForEveryToolHasItsMessages() {
    for (tool in OxcTool.entries) {
      for (result in RESULTS) {
        val notification = fixAllNotification(project, tool, result, "a.js") {}
        val texts = listOf(notification.title, notification.content) + notification.actions.map { it.templateText }
        for (text in texts) {
          assertFalse("$tool, $result: \"$text\"", text.isNullOrBlank() || text.startsWith("!"))
        }
      }
    }
  }

  private companion object {
    val RESULTS = listOf(
      OxcFixAllResult.Applied,
      OxcFixAllResult.NothingToApply,
      OxcFixAllResult.UnsupportedFile,
      OxcFixAllResult.FixesTurnedOff,
      OxcFixAllResult.Untrusted,
      OxcFixAllResult.FileUnavailable,
      OxcFixAllResult.ToolNotFound,
      OxcFixAllResult.TimedOut,
      OxcFixAllResult.Failed("the server failed"),
    )

    /** Doesn't compile once a result is added, so [RESULTS] gets it too. */
    @Suppress("unused")
    fun covered(result: OxcFixAllResult): Unit = when (result) {
      OxcFixAllResult.Applied, OxcFixAllResult.NothingToApply, OxcFixAllResult.UnsupportedFile,
      OxcFixAllResult.FixesTurnedOff, OxcFixAllResult.Untrusted, OxcFixAllResult.FileUnavailable,
      OxcFixAllResult.ToolNotFound, OxcFixAllResult.TimedOut, is OxcFixAllResult.Failed -> Unit
    }
  }
}
