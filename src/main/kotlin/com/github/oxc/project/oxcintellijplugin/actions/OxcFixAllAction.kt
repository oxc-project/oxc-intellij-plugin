package com.github.oxc.project.oxcintellijplugin.actions

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.services.OxcServerService
import com.github.oxc.project.oxcintellijplugin.settings.OxcConfigurable
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.readText
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

class OxcFixAllAction : AnAction(), DumbAware {
    init {
        templatePresentation.icon = OxcIcons.OxcRound
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val document = event.getOrCreateDocument() ?: return

        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")

        val settings = OxcSettings.getInstance(project)
        val manager = FileDocumentManager.getInstance()
        val virtualFile = manager.getFile(document) ?: return

        if (!settings.fileSupported(virtualFile)) {
            notificationGroup.createNotification(title = OxcBundle.message("oxc.file.not.supported.title"),
                content = OxcBundle.message("oxc.file.not.supported.description", virtualFile.name),
                type = NotificationType.WARNING)
                .addAction(NotificationAction.createSimple(OxcBundle.message("oxc.configure.extensions.link")) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, OxcConfigurable::class.java)
                }).notify(project)
            return
        }

        runWithModalProgressBlocking(project,
            OxcBundle.message("oxc.run.fix.all")) {
            try {
                withTimeout(5_000) {
                    OxcServerService.getInstance(project).fixAll(document)
                }
                notificationGroup.createNotification(title = OxcBundle.message("oxc.fix.all.success.label"),
                    content = OxcBundle.message("oxc.fix.all.success.description"),
                    type = NotificationType.INFORMATION).notify(project)
            } catch (e: Exception) {
                notificationGroup.createNotification(title = OxcBundle.message("oxc.fix.all.failure.label"),
                    content = OxcBundle.message("oxc.fix.all.failure.description", e.message.toString()),
                    type = NotificationType.ERROR).notify(project)
            }
        }
    }
    
    private fun AnActionEvent.getOrCreateDocument(): Document? {
        getData(CommonDataKeys.EDITOR)?.let {
            return it.document
        }
        
        val file = getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        val text = try {
            file.readText()
        } catch (_: IOException) {
            return null
        }
        
        return EditorFactory.getInstance().createDocument(text)
    }
}
