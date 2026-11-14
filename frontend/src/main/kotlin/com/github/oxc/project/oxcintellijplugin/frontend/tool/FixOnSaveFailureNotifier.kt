@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.tool

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixOnSaveFailure
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows each fix on save that the backend says failed. Only the newest failure of each tool stays, so a
 * Save All that fails for every file doesn't leave a balloon per file.
 */
@Service(Service.Level.PROJECT)
class FixOnSaveFailureNotifier(private val project: Project, scope: CoroutineScope) {

  private val shown = ConcurrentHashMap<OxcTool, Notification>()

  init {
    if (!project.isDefault) {
      scope.launch {
        durable { OxcToolApi.getInstance().fixOnSaveFailures(project.projectId()).collect(::show) }
      }
    }
  }

  private fun show(failure: OxcFixOnSaveFailure) {
    val notification = fixOnSaveFailureNotification(failure) ?: return
    shown.put(failure.tool, notification)?.expire()
    notification.notify(project)
  }
}

/** The balloon for [failure], or null for a result that isn't a failure. Public for tests. */
fun fixOnSaveFailureNotification(failure: OxcFixOnSaveFailure): Notification? {
  val prefix = failure.tool.messagePrefix
  val fileName = StringUtil.escapeXmlEntities(failure.fileName)
  val (titleKey, content) = when (val result = failure.result) {
    OxcFixAllResult.TimedOut ->
      "$prefix.fix.on.save.timeout.label" to OxcFrontendBundle.message("$prefix.fix.on.save.timeout.description", fileName)
    is OxcFixAllResult.Failed ->
      "$prefix.fix.on.save.failure.label" to
        OxcFrontendBundle.message("$prefix.fix.on.save.failure.description", fileName, StringUtil.escapeXmlEntities(result.message))
    else -> return null
  }
  return NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
    .createNotification(OxcFrontendBundle.message(titleKey), content, NotificationType.ERROR)
}

internal class FixOnSaveFailureNotifierStarter : ProjectActivity {

  override suspend fun execute(project: Project) {
    project.service<FixOnSaveFailureNotifier>()
  }
}
