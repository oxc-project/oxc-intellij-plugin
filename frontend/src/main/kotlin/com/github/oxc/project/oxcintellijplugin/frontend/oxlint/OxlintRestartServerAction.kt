@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxlint

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.OxcIcons
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OxlintRestartServerAction : AnAction(), DumbAware {

  init {
    templatePresentation.icon = OxcIcons.OxcRound
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.service<OxlintServerRestarter>().restart()
  }
}

@Service(Service.Level.PROJECT)
internal class OxlintServerRestarter(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  fun restart() {
    coroutineScope.launch {
      durable { OxlintServerApi.getInstance().restart(project.projectId()) }
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Oxc")
        .createNotification(
          OxcFrontendBundle.message("oxlint.language.server.restarted"),
          NotificationType.INFORMATION,
        )
        .notify(project)
    }
  }
}
