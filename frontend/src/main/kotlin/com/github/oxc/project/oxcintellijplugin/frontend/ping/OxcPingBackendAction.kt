@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.ping

import com.github.oxc.project.oxcintellijplugin.shared.ping.OxcPingApi
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Throwaway action that shows both process ids. They differ in split mode and match in a monolith
 * IDE. Remove it when the first real frontend action is added.
 */
internal class OxcPingBackendAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<OxcPingScope>().ping(project)
    }
}

@Service(Service.Level.PROJECT)
internal class OxcPingScope(private val coroutineScope: CoroutineScope) {
    fun ping(project: Project) {
        coroutineScope.launch {
            val result = durable { OxcPingApi.getInstance().ping() }
            val frontendProcessId = ProcessHandle.current().pid()
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Oxc")
                .createNotification(
                    "Backend process ${result.backendProcessId} answered frontend process $frontendProcessId",
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }
    }
}
