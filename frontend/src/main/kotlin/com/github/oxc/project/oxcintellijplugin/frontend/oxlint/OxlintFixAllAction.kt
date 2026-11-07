@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxlint

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.OxcIcons
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintSettingsCache
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.isOxlintSupportedExtension
import com.intellij.ide.vfs.rpcId
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class OxlintFixAllAction : AnAction(), DumbAware {

  init {
    templatePresentation.icon = OxcIcons.OxcRound
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val settings = e.project?.service<OxlintSettingsCache>()?.current
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val enabled = file != null && settings != null &&
                  settings.configurationMode != ConfigurationMode.DISABLED &&
                  isOxlintSupportedExtension(settings.supportedExtensions, file.extension)

    if (e.isFromContextMenu) e.presentation.isVisible = enabled
    e.presentation.isEnabled = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    project.service<OxlintFixAllRunner>().run(file)
  }
}

/** Runs in the project's scope, because the action has returned by the time the backend answers. */
@Service(Service.Level.PROJECT)
internal class OxlintFixAllRunner(private val project: Project, private val scope: CoroutineScope) {

  /**
   * A modal dialog stops the file changing while the server works out edits for it, without blocking
   * the UI thread while waiting for the backend.
   */
  fun run(file: VirtualFile) {
    val fileId = file.rpcId()
    scope.launch {
      val result = withModalProgress(
        ModalTaskOwner.project(project),
        OxcFrontendBundle.message("oxlint.run.fix.all"),
        TaskCancellation.cancellable(),
      ) {
        durable { OxlintServerApi.getInstance().fixAll(project.projectId(), fileId) }
      }
      notify(result, file)
    }
  }

  private fun notify(result: OxlintFixAllResult, file: VirtualFile) {
    when (result) {
      OxlintFixAllResult.NothingToApply -> group().createNotification(
        OxcFrontendBundle.message("oxlint.fix.all.nothing.label"),
        OxcFrontendBundle.message("oxlint.fix.all.nothing.description", file.name),
        NotificationType.INFORMATION,
      ).notify(project)
      OxlintFixAllResult.FixesTurnedOff -> group().createNotification(
        OxcFrontendBundle.message("oxlint.fix.all.turned.off.label"),
        OxcFrontendBundle.message("oxlint.fix.all.turned.off.description"),
        NotificationType.INFORMATION,
      ).addAction(openSettings("oxlint.open.settings.link")).notify(project)
      OxlintFixAllResult.OxlintNotFound -> group().createNotification(
        OxcFrontendBundle.message("oxlint.fix.all.not.found.label"),
        OxcFrontendBundle.message("oxlint.fix.all.not.found.description", file.name),
        NotificationType.WARNING,
      ).addAction(openSettings("oxlint.open.settings.link")).notify(project)
      OxlintFixAllResult.Applied -> notification(
        "oxlint.fix.all.success.label", "oxlint.fix.all.success.description", NotificationType.INFORMATION).notify(project)
      OxlintFixAllResult.TimedOut -> notification(
        "oxlint.fix.all.timeout.label", "oxlint.fix.all.timeout.description", NotificationType.WARNING).notify(project)
      is OxlintFixAllResult.Failed -> group().createNotification(
        OxcFrontendBundle.message("oxlint.fix.all.failure.label"),
        OxcFrontendBundle.message("oxlint.fix.all.failure.description", result.message),
        NotificationType.ERROR,
      ).notify(project)
      OxlintFixAllResult.UnsupportedFile -> group().createNotification(
        OxcFrontendBundle.message("oxlint.file.not.supported.title"),
        OxcFrontendBundle.message("oxlint.file.not.supported.description", file.name),
        NotificationType.WARNING,
      ).addAction(openSettings("oxlint.configure.extensions.link")).notify(project)
    }
  }

  private fun openSettings(textKey: String): NotificationAction =
    NotificationAction.createSimple(OxcFrontendBundle.message(textKey)) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, OxlintConfigurable::class.java)
    }

  private fun notification(titleKey: String, contentKey: String, type: NotificationType): Notification =
    group().createNotification(OxcFrontendBundle.message(titleKey), OxcFrontendBundle.message(contentKey), type)

  private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
}
