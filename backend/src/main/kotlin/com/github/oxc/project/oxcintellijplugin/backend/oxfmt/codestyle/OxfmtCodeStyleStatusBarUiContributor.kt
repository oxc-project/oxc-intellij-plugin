package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor

/** The backend's own notification group, because the `Oxc` group is on the frontend. */
internal const val CODE_STYLE_NOTIFICATION_GROUP = "Oxc Code Style"

class OxfmtCodeStyleStatusBarUiContributor : CodeStyleStatusBarUIContributor {

  override fun areActionsAvailable(file: VirtualFile): Boolean = true

  override fun getActions(file: PsiFile): Array<AnAction> = arrayOf(
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.open.configuration.file.label")) {
      openConfig(file)
    },
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.open.settings.label")) {
      ShowSettingsUtil.getInstance().showSettingsDialog(file.project, "Oxfmt")
    },
  )

  private fun openConfig(file: PsiFile) {
    val project = file.project
    val path = file.virtualFile ?: return
    val config = findOxfmtConfig(project, path)
    if (config != null) {
      OpenFileDescriptor(project, config).navigate(true)
    }
    else {
      NotificationGroupManager.getInstance().getNotificationGroup(CODE_STYLE_NOTIFICATION_GROUP).createNotification(
        OxcBackendBundle.message("oxfmt.configuration.file.not.found.title"),
        OxcBackendBundle.message("oxfmt.configuration.file.not.found.description", path.path),
        NotificationType.INFORMATION,
      ).notify(project)
    }
  }

  override fun getTooltip(): String? = null

  override fun createDisableAction(project: Project): AnAction =
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.disable.for.project.label")) {
      disableOxfmtCodeStyle(project)
      NotificationGroupManager.getInstance().getNotificationGroup(CODE_STYLE_NOTIFICATION_GROUP).createNotification(
        OxcBackendBundle.message("oxfmt.code.style.preference.disabled.title"),
        OxcBackendBundle.message("oxfmt.code.style.preference.disabled.description"),
        NotificationType.INFORMATION,
      ).notify(project)
    }

  override fun getActionGroupTitle(): String = OxcBackendBundle.message("oxfmt.code.style.status.bar.action.group.title")
}
