@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxfmt

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.callBackendReportingFailure
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleNotice
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtJsConfigFailure
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.project.projectId
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.Action
import javax.swing.JComponent

private const val MAX_MESSAGE_LINES = 5

/**
 * Shows what the backend sends when it can't read a JavaScript or TypeScript config, or when one of the code
 * style status bar's actions, which run on the backend, has something to say. The backend decides when to
 * send one, so this shows each.
 */
@Service(Service.Level.PROJECT)
class OxfmtCodeStyleNotifier(private val project: Project, private val scope: CoroutineScope) {

  init {
    if (!project.isDefault) {
      scope.launch {
        durable { OxfmtCodeStyleApi.getInstance().notices(project.projectId()).collect(::show) }
      }
    }
  }

  private fun show(notice: OxfmtCodeStyleNotice) {
    when (notice) {
      is OxfmtCodeStyleNotice.Untrusted -> untrusted(notice)
      is OxfmtCodeStyleNotice.Failed -> failed(notice)
      OxfmtCodeStyleNotice.CodeStyleDisabled -> group().createNotification(
        OxcFrontendBundle.message("oxfmt.code.style.preference.disabled.title"),
        OxcFrontendBundle.message("oxfmt.code.style.preference.disabled.description"),
        NotificationType.INFORMATION,
      )
      is OxfmtCodeStyleNotice.ConfigNotFound -> group().createNotification(
        OxcFrontendBundle.message("oxfmt.configuration.file.not.found.title"),
        OxcFrontendBundle.message("oxfmt.configuration.file.not.found.description", notice.fileName),
        NotificationType.INFORMATION,
      )
    }.notify(project)
  }

  private fun untrusted(notice: OxfmtCodeStyleNotice.Untrusted): Notification =
    group().createNotification(
      OxcFrontendBundle.message("oxfmt.js.config.untrusted.title"),
      OxcFrontendBundle.message("oxfmt.js.config.untrusted.description", notice.configName),
      NotificationType.WARNING,
    ).addAction(NotificationAction.createSimpleExpiring(OxcFrontendBundle.message("trust.project")) {
      // The backend holds the project's trust, and asks with the platform's dialog. Trust refreshes the overlay.
      scope.launch { durable { OxcToolApi.getInstance().trustProject(project.projectId()) } }
    })

  private fun failed(notice: OxfmtCodeStyleNotice.Failed): Notification {
    val title = OxcFrontendBundle.message("oxfmt.js.config.failed.title", notice.configName)
    val notification = group().createNotification(
      title,
      StringUtil.escapeXmlEntities(firstLines(notice.message)).replace("\n", "<br>"),
      NotificationType.WARNING,
    )
    when (notice.reason) {
      OxfmtJsConfigFailure.NO_NODE, OxfmtJsConfigFailure.OLD_NODE -> notification.addAction(
        NotificationAction.createSimple(OxcFrontendBundle.message("oxfmt.js.config.configure.node")) {
          backend("oxfmt.js.config.configure.node.failed") { showNodeSettings(project.projectId()) }
        })
      OxfmtJsConfigFailure.ERROR -> notification.addAction(
        NotificationAction.createSimple(OxcFrontendBundle.message("oxfmt.js.config.show.details")) {
          OxfmtConfigDetailsDialog(project, title, notice.details).show()
        })
    }
    return notification.addAction(editConfig(notice.configUrl))
  }

  private fun editConfig(configUrl: String): NotificationAction =
    NotificationAction.createSimple(OxcFrontendBundle.message("oxfmt.js.config.edit.configuration.file")) {
      backend("oxfmt.js.config.edit.configuration.file.failed") { openConfig(project.projectId(), configUrl) }
    }

  /** The Node.js page and the config are the backend's, so these actions ask it. */
  private fun backend(failureKey: String, call: suspend OxfmtCodeStyleApi.() -> Unit) {
    scope.launch {
      callBackendReportingFailure(project, failureKey) { durable { OxfmtCodeStyleApi.getInstance().call() } }
    }
  }

  private fun firstLines(message: String): String {
    val lines = message.trim().lines()
    return if (lines.size <= MAX_MESSAGE_LINES) lines.joinToString("\n") else (lines.take(MAX_MESSAGE_LINES) + "…").joinToString("\n")
  }

  private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
}

internal class OxfmtCodeStyleNotifierStarter : ProjectActivity {

  override suspend fun execute(project: Project) {
    project.service<OxfmtCodeStyleNotifier>()
  }
}

/** The whole error, which can be selected and copied, because the notification only has its first lines. */
private class OxfmtConfigDetailsDialog(project: Project, title: String, private val details: String) : DialogWrapper(project) {

  init {
    this.title = title
    init()
  }

  override fun createCenterPanel(): JComponent {
    val text = JBTextArea(details).apply {
      isEditable = false
      font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
      caretPosition = 0
    }
    return JBScrollPane(text).apply { preferredSize = JBUI.size(720, 360) }
  }

  override fun createActions(): Array<Action> = arrayOf(okAction)
}
