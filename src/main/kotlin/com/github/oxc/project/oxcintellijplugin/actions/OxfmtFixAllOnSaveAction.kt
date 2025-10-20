package com.github.oxc.project.oxcintellijplugin.actions

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.github.oxc.project.oxcintellijplugin.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.settings.OxfmtSettings
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

    override fun processDocuments(project: Project,
        documents: Array<Document>) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")

        runWithModalProgressBlocking(project,
            OxcBundle.message("oxlint.run.fix.all")) {
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
                    title = OxcBundle.message("oxlint.fix.all.on.save.failure.label"),
                    content = OxcBundle.message(
                        "oxlint.fix.all.on.save.failure.description",
                        e.message.toString()
                    ),
                    type = NotificationType.ERROR).notify(project)
            }
        }
    }
}
