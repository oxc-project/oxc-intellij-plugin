package com.github.oxc.project.oxcintellijplugin.frontend

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CancellationException

private object BackendCalls

/**
 * Runs a call to the backend, and shows a failure as a balloon titled with [messageKey]. Returns null if
 * it failed. Uncaught, the backend's exception would only reach the IDE's error reporter, because
 * `durable` retries a lost connection but not an exception that the backend threw.
 */
suspend fun <T : Any> callBackendReportingFailure(
  project: Project,
  messageKey: String,
  call: suspend () -> T,
): T? =
  try {
    call()
  }
  catch (exception: CancellationException) {
    throw exception
  }
  catch (exception: Exception) {
    logger<BackendCalls>().warn(exception)
    NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
      .createNotification(
        OxcFrontendBundle.message(messageKey),
        StringUtil.escapeXmlEntities(exception.message.orEmpty()),
        NotificationType.ERROR,
      )
      .notify(project)
    null
  }
