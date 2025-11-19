package com.github.oxc.project.oxcintellijplugin.actions

import com.github.oxc.project.oxcintellijplugin.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.services.OxcServerService
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.withTimeout

class OxcFixAllOnSaveAction : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean {
        return OxcSettings.getInstance(project).fixAllOnSave
    }

    override fun processDocuments(project: Project,
        documents: Array<Document>) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")

        runWithModalProgressBlocking(project,
            OxlintBundle.message("oxc.run.fix.all")) {
            try {
                withTimeout(5_000) {
                    documents.filter {
                        val settings = OxcSettings.getInstance(project)
                        val manager = FileDocumentManager.getInstance()
                        val virtualFile = manager.getFile(it) ?: return@filter false
                        return@filter settings.fileSupported(virtualFile)
                    }.forEach {
                        OxcServerService.getInstance(project).fixAll(it)
                    }
                }
            } catch (e: Exception) {
                notificationGroup.createNotification(
                    title = OxlintBundle.message("oxc.fix.all.on.save.failure.label"),
                    content = OxlintBundle.message(
                        "oxc.fix.all.on.save.failure.description",
                        e.message.toString()
                    ),
                    type = NotificationType.ERROR).notify(project)
            }
        }
    }
}
