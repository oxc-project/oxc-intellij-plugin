@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.tool

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
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

internal abstract class ToolRestartServerAction(private val tool: OxcTool) : AnAction(), DumbAware {

  init {
    templatePresentation.icon = OxcIcons.OxcRound
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.service<ToolServerRestarter>().restart(tool)
  }
}

internal class OxlintRestartServerAction : ToolRestartServerAction(OxcTool.OXLINT)

internal class OxfmtRestartServerAction : ToolRestartServerAction(OxcTool.OXFMT)

@Service(Service.Level.PROJECT)
internal class ToolServerRestarter(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  fun restart(tool: OxcTool) {
    coroutineScope.launch {
      durable { OxcToolApi.getInstance().restart(project.projectId(), tool) }
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Oxc")
        .createNotification(
          OxcFrontendBundle.message("${tool.messagePrefix}.language.server.restarted"),
          NotificationType.INFORMATION,
        )
        .notify(project)
    }
  }
}
