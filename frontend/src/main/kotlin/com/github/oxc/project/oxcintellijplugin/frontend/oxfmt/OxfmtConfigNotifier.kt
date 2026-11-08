@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxfmt

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtConfigNotice
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
 * Shows what the backend sends when it can't read a JavaScript or TypeScript config. The backend decides
 * when to send one, so this shows each.
 */
@Service(Service.Level.PROJECT)
class OxfmtConfigNotifier(private val project: Project, private val scope: CoroutineScope) {

  init {
    if (!project.isDefault) {
      scope.launch {
        durable { OxfmtCodeStyleApi.getInstance().configNotices(project.projectId()).collect(::show) }
      }
    }
  }

  private fun show(notice: OxfmtConfigNotice) {
    when (notice) {
      is OxfmtConfigNotice.Untrusted -> untrusted(notice)
      is OxfmtConfigNotice.Failed -> failed(notice)
    }.notify(project)
  }

  private fun untrusted(notice: OxfmtConfigNotice.Untrusted): Notification =
    group().createNotification(
      OxcFrontendBundle.message("oxfmt.js.config.untrusted.title"),
      OxcFrontendBundle.message("oxfmt.js.config.untrusted.description", notice.configName),
      NotificationType.WARNING,
    ).addAction(NotificationAction.createSimpleExpiring(OxcFrontendBundle.message("oxfmt.js.config.trust.project")) {
      // The backend holds the project's trust.
      backend { trustProject(project.projectId()) }
    })

  private fun failed(notice: OxfmtConfigNotice.Failed): Notification {
    val title = OxcFrontendBundle.message("oxfmt.js.config.failed.title", notice.configName)
    val notification = group().createNotification(
      title,
      StringUtil.escapeXmlEntities(firstLines(notice.message)).replace("\n", "<br>"),
      NotificationType.WARNING,
    )
    when (notice.reason) {
      OxfmtJsConfigFailure.NO_NODE, OxfmtJsConfigFailure.OLD_NODE -> notification.addAction(
        NotificationAction.createSimple(OxcFrontendBundle.message("oxfmt.js.config.configure.node")) {
          backend { showNodeSettings(project.projectId()) }
        })
      OxfmtJsConfigFailure.ERROR -> notification.addAction(
        NotificationAction.createSimple(OxcFrontendBundle.message("oxfmt.js.config.show.details")) {
          OxfmtConfigDetailsDialog(project, title, notice.details).show()
        })
    }
    return notification.addAction(
      NotificationAction.createSimple(OxcFrontendBundle.message("oxfmt.js.config.edit.configuration.file")) {
        backend { openConfig(project.projectId(), notice.configUrl) }
      })
  }

  /** Trust, the Node.js page and the config are all the backend's, so these actions ask it. */
  private fun backend(call: suspend OxfmtCodeStyleApi.() -> Unit) {
    scope.launch { durable { OxfmtCodeStyleApi.getInstance().call() } }
  }

  private fun firstLines(message: String): String {
    val lines = message.trim().lines()
    return if (lines.size <= MAX_MESSAGE_LINES) lines.joinToString("\n") else (lines.take(MAX_MESSAGE_LINES) + "…").joinToString("\n")
  }

  private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
}

internal class OxfmtConfigNotifierStarter : ProjectActivity {

  override suspend fun execute(project: Project) {
    project.service<OxfmtConfigNotifier>()
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
