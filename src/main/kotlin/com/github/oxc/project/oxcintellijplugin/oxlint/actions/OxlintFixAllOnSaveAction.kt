package com.github.oxc.project.oxcintellijplugin.oxlint.actions

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.time.Duration
import kotlinx.coroutines.withTimeout

class OxlintFixAllOnSaveAction : ActionsOnSaveFileDocumentManagerListener.ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean {
        return OxlintSettings.getInstance(project).fixAllOnSave
    }

    override fun processDocuments(project: Project,
        documents: Array<Document>) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)

        val logger = thisLogger()
        runWithModalProgressBlocking(project,
            OxlintBundle.message("oxlint.run.fix.all")) {
            try {
                withTimeout(5_000) {
                    documents.forEach {
                        val start = System.nanoTime()
                        val settings = OxlintSettings.getInstance(project)
                        val manager = FileDocumentManager.getInstance()
                        val virtualFile = manager.getFile(it)
                        val intellijEnd = System.nanoTime()
                        if (virtualFile == null || !settings.fileSupported(virtualFile)) {
                            logger.debug("Virtual file is null or file is not supported. IntelliJ Duration: ${Duration.ofNanos(intellijEnd - start).toMillis()}ms")
                            return@forEach
                        }

                        OxlintServerService.getInstance(project).fixAll(it)

                        val oxlintEnd = System.nanoTime()
                        logger.debug("Fix for file ${virtualFile.path} is complete. IntelliJ Duration: ${
                            Duration.ofNanos(intellijEnd - start).toMillis()
                        }ms, Oxlint Duration: ${Duration.ofNanos(oxlintEnd - intellijEnd).toMillis()}ms")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Exception while applying oxlint fixes on save", e);
                notificationGroup.createNotification(
                    title = OxlintBundle.message("oxlint.fix.all.on.save.failure.label"),
                    content = OxlintBundle.message(
                        "oxlint.fix.all.on.save.failure.description",
                        e.message.toString()
                    ),
                    type = NotificationType.ERROR).notify(project)
            }
        }
    }
}
