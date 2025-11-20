package com.github.oxc.project.oxcintellijplugin.oxfmt.actions

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.io.IOException
import kotlinx.coroutines.withTimeout

class OxfmtFixAllAction : AnAction(), DumbAware {
    init {
        templatePresentation.icon = OxcIcons.OxcRound
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val (virtualFile, document) = event.getFileAndDocument() ?: return

        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)

        val settings = OxfmtSettings.getInstance(project)

        if (!settings.fileSupported(virtualFile)) {
            notificationGroup.createNotification(
                title = OxfmtBundle.message("oxfmt.file.not.supported.title"),
                content = OxfmtBundle.message("oxfmt.file.not.supported.description",
                    virtualFile.name),
                type = NotificationType.WARNING)
                .addAction(NotificationAction.createSimple(
                    OxfmtBundle.message("oxfmt.configure.extensions.link")) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, OxfmtConfigurable::class.java)
                }).notify(project)
            return
        }

        runWithModalProgressBlocking(project,
            OxfmtBundle.message("oxfmt.run.fix.all")) {
            try {
                withTimeout(5_000) {
                    OxfmtServerService.getInstance(project).fixAll(virtualFile, document)
                }
                notificationGroup.createNotification(
                    title = OxfmtBundle.message("oxfmt.fix.all.success.label"),
                    content = OxfmtBundle.message("oxfmt.fix.all.success.description"),
                    type = NotificationType.INFORMATION).notify(project)
            } catch (e: Exception) {
                notificationGroup.createNotification(
                    title = OxfmtBundle.message("oxfmt.fix.all.failure.label"),
                    content = OxfmtBundle.message("oxfmt.fix.all.failure.description",
                        e.message.toString()),
                    type = NotificationType.ERROR).notify(project)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = OxfmtSettings.getInstance(project)

        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val enabled = file != null && settings.fileSupported(file) && settings.isEnabled()

        if (e.isFromContextMenu) {
            e.presentation.isVisible = enabled
        }
        e.presentation.isEnabled = enabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun AnActionEvent.getFileAndDocument(): Pair<VirtualFile, Document>? {
        getData(CommonDataKeys.EDITOR)?.let {
            val document = it.document

            val manager = FileDocumentManager.getInstance()
            val file = manager.getFile(document) ?: return null

            return file to document
        }

        val file = getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        val text = try {
            file.readText()
        } catch (_: IOException) {
            return null
        }

        val document = EditorFactory.getInstance().createDocument(text)

        return file to document
    }
}
