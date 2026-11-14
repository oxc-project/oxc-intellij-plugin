package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The backend's own notification group, because the `Oxc` group is on the frontend. Registered in the shared module. */
internal const val CODE_STYLE_NOTIFICATION_GROUP = "Oxc Code Style"

class OxfmtCodeStyleStatusBarUiContributor : CodeStyleStatusBarUIContributor {

  override fun areActionsAvailable(file: VirtualFile): Boolean = true

  override fun getActions(file: PsiFile): Array<AnAction> = arrayOf(
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.open.configuration.file.label")) {
      val path = file.virtualFile ?: return@create
      file.project.service<OxfmtConfigOpener>().open(path)
    },
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.open.settings.label")) {
      openOxfmtSettings(file.project)
    },
  )

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

/** By the page's id, because its display name is translated. Public for tests. */
fun isOxfmtSettingsPage(configurable: Configurable): Boolean =
  (configurable as? SearchableConfigurable)?.id == OxfmtDefinition.configurableId

private fun openOxfmtSettings(project: Project) {
  ShowSettingsUtil.getInstance().showSettingsDialog(project, ::isOxfmtSettingsPage, null)
}

/**
 * Opens the config that applies to a file. The lookup runs off the EDT, because without a running server
 * it finds the oxfmt package on disk, which is slow over `\\wsl$` or a remote file system.
 */
@Service(Service.Level.PROJECT)
internal class OxfmtConfigOpener(private val project: Project, private val scope: CoroutineScope) {

  fun open(file: VirtualFile) {
    scope.launch {
      val config = readAction { findOxfmtConfig(project, file) }
      withContext(Dispatchers.EDT) {
        if (config != null) {
          OpenFileDescriptor(project, config).navigate(true)
        }
        else {
          NotificationGroupManager.getInstance().getNotificationGroup(CODE_STYLE_NOTIFICATION_GROUP).createNotification(
            OxcBackendBundle.message("oxfmt.configuration.file.not.found.title"),
            OxcBackendBundle.message("oxfmt.configuration.file.not.found.description", file.path),
            NotificationType.INFORMATION,
          ).notify(project)
        }
      }
    }
  }
}
