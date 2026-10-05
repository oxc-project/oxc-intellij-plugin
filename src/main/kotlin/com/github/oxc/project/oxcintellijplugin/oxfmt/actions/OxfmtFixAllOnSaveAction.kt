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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class OxfmtFixAllOnSaveAction : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {

    override val presentableName: String
        get() = OxfmtBundle.message("oxfmt.run.fix.all")

    override fun isEnabledForProject(project: Project): Boolean {
        return OxfmtSettings.getInstance(project).fixAllOnSave
    }

    override suspend fun updateDocument(project: Project, document: Document) {
        val settings = OxfmtSettings.getInstance(project)
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!settings.fileSupported(virtualFile)) {
            return
        }

        try {
            withTimeout(5_000) {
                OxfmtServerService.getInstance(project).fixAll(virtualFile, document)
            }
        } catch (e: TimeoutCancellationException) {
            notifyFailure(project, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            notifyFailure(project, e)
        }
    }

    private fun notifyFailure(project: Project, exception: Exception) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                title = OxfmtBundle.message("oxfmt.fix.all.on.save.failure.label"),
                content = OxfmtBundle.message("oxfmt.fix.all.on.save.failure.description",
                    exception.message.toString()),
                type = NotificationType.ERROR).notify(project)
    }
}
