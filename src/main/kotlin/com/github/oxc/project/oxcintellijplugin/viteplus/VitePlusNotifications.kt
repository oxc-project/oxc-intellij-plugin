package com.github.oxc.project.oxcintellijplugin.viteplus

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class VitePlusNotifications(private val project: Project) {
    private val notifications = ConcurrentHashMap<String, Notification>()

    fun unavailable(root: String, configuredPath: String) {
        show("missing:$root:$configuredPath", if (configuredPath.isBlank()) {
            "Vite+ detected in $root. Run your package manager's install command (for example, pnpm install) " +
                "or install Vite+ globally, then restart the Oxc servers."
        } else {
            "Vite+ executable not found: $configuredPath. Correct the Vite+ path in the tool settings, then restart the server."
        })
    }

    fun launchFailed(root: String, tool: String) {
        show("launch:$tool:$root", "Vite+ could not start the $tool language server in $root. Check the language server log, " +
            "upgrade vite-plus, and restart the server. If Node is not on PATH, use vite-plus 0.3.2 or later.")
    }

    fun resolved(root: String, configuredPath: String) {
        notifications.remove("missing:$root:$configuredPath")?.expire()
    }

    fun started(root: String, tool: String) {
        notifications.remove("launch:$tool:$root")?.expire()
    }

    private fun show(key: String, message: String) {
        if (project.isDisposed) return
        notifications.computeIfAbsent(key) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Vite+ language server", message, NotificationType.WARNING)
                .also { it.notify(project) }
        }
    }

    companion object {
        fun getInstance(project: Project): VitePlusNotifications = project.getService(VitePlusNotifications::class.java)
    }
}
