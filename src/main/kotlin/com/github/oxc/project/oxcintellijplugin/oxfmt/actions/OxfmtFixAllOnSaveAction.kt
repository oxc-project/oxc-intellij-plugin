package com.github.oxc.project.oxcintellijplugin.oxfmt.actions

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.withTimeout

class OxfmtFixAllOnSaveAction : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {

    override fun isEnabledForProject(project: Project): Boolean {
        return OxfmtSettings.getInstance(project).fixAllOnSave
    }

    override fun processDocuments(project: Project, documents: Array<Document>) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)

        runWithModalProgressBlocking(project, OxfmtBundle.message("oxfmt.run.fix.all")) {
            try {
                withTimeout(5_000) {
                    documents.filter {
                        val settings = OxfmtSettings.getInstance(project)
                        val manager = FileDocumentManager.getInstance()
                        val virtualFile = manager.getFile(it) ?: return@filter false
                        return@filter settings.fileSupported(virtualFile)
                    }.forEach {
                        OxfmtServerService.getInstance(project).fixAll(it)
                    }
                }
            } catch (e: Exception) {
                notificationGroup.createNotification(
                    title = OxfmtBundle.message("oxfmt.fix.all.on.save.failure.label"),
                    content = OxfmtBundle.message("oxfmt.fix.all.on.save.failure.description",
                        e.message.toString()), type = NotificationType.ERROR).notify(project)
            }
        }
    }
}
