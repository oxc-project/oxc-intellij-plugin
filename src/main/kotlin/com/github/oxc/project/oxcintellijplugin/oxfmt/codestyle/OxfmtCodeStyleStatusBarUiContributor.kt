package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.extensions.findNearestOxfmtJsonConfigFile
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor

class OxfmtCodeStyleStatusBarUiContributor : CodeStyleStatusBarUIContributor {

    override fun areActionsAvailable(file: VirtualFile): Boolean {
        return true
    }

    override fun getActions(file: PsiFile): Array<out AnAction?> {
        return buildList {
            add(object :
                DumbAwareAction(OxfmtBundle.message("oxfmt.action.open.configuration.file.label")) {
                override fun actionPerformed(e: AnActionEvent) {
                    val configFile = file.virtualFile.findNearestOxfmtJsonConfigFile(
                        file.project.guessProjectDir())
                    if (configFile != null) {
                        val fileEditorManager = FileEditorManager.getInstance(file.project)
                        if (fileEditorManager.isFileOpen(configFile)) {
                            fileEditorManager.closeFile(configFile)
                        }
                        fileEditorManager.openFile(configFile, true)
                    } else {
                        thisLogger().warn("No config file found for file ${file.virtualFile.path}")
                        val notificationGroup = NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP)
                        val notification = notificationGroup.createNotification(
                            OxfmtBundle.message("oxfmt.configuration.file.not.found.title"),
                            OxfmtBundle.message("oxfmt.configuration.file.not.found.description",
                                file.virtualFile.path),
                            NotificationType.INFORMATION)
                        notification.notify(file.project)
                    }
                }
            })
            add(object : DumbAwareAction(OxfmtBundle.message("oxfmt.action.open.settings.label")) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtilImpl.showSettingsDialog(e.project,
                        OxfmtConfigurable.CONFIGURABLE_ID, "Oxfmt")
                }
            })
        }.toTypedArray()
    }

    override fun getTooltip(): @NlsContexts.Tooltip String? {
        return null
    }

    override fun createDisableAction(project: Project): AnAction {
        return DumbAwareAction.create(
            OxfmtBundle.message("oxfmt.action.disable.for.project.label")) { _ ->
            OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = false
            CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()

            thisLogger().info("Oxfmt code style has been disabled for the current project")
            val notificationGroup = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
            val notification = notificationGroup.createNotification(
                OxfmtBundle.message("oxfmt.code.style.preference.disabled.title"),
                OxfmtBundle.message("oxfmt.code.style.preference.disabled.description"),
                NotificationType.INFORMATION)
            notification.notify(project)
        }
    }

    override fun getActionGroupTitle(): @NlsContexts.PopupTitle String {
        return OxfmtBundle.message("oxfmt.code.style.status.bar.action.group.title")
    }

}
