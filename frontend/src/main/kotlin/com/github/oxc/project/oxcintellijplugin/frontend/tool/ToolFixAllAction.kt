@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.tool

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.frontend.settings.OxcSettingsCache
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.intellij.ide.vfs.rpcId
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal abstract class ToolFixAllAction(private val tool: OxcTool) : AnAction(), DumbAware {

  init {
    templatePresentation.icon = OxcIcons.OxcRound
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val enabled = file != null && e.project?.service<OxcSettingsCache>()?.isEnabledFor(tool, file) == true

    if (e.isFromContextMenu) e.presentation.isVisible = enabled
    e.presentation.isEnabled = enabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    project.service<ToolFixAllRunner>().run(tool, file)
  }
}

internal class OxlintFixAllAction : ToolFixAllAction(OxcTool.OXLINT)

internal class OxfmtFixAllAction : ToolFixAllAction(OxcTool.OXFMT)

/** Runs in the project's scope, because the action has returned by the time the backend answers. */
@Service(Service.Level.PROJECT)
internal class ToolFixAllRunner(private val project: Project, private val scope: CoroutineScope) {

  /**
   * A modal dialog stops the file changing while the server works out edits for it, without blocking
   * the UI thread while waiting for the backend. The server starts before it, in the background,
   * because the platform's start waits for a modal dialog to close. A closed file has no server yet.
   */
  fun run(tool: OxcTool, file: VirtualFile) {
    val fileId = file.rpcId()
    val title = OxcFrontendBundle.message("${tool.messagePrefix}.run.fix.all")
    scope.launch {
      val stop = withBackgroundProgress(project, title, TaskCancellation.cancellable()) {
        durable { OxcToolApi.getInstance().startServerForFixAll(project.projectId(), tool, fileId) }
      }
      val result = stop ?: withModalProgress(ModalTaskOwner.project(project), title, TaskCancellation.cancellable()) {
        durable { OxcToolApi.getInstance().fixAll(project.projectId(), tool, fileId) }
      }
      notify(tool, result, file)
    }
  }

  private fun notify(tool: OxcTool, result: OxcFixAllResult, file: VirtualFile) {
    val prefix = tool.messagePrefix
    val notification = when (result) {
      OxcFixAllResult.NothingToApply -> notification(
        "$prefix.fix.all.nothing.label",
        OxcFrontendBundle.message("$prefix.fix.all.nothing.description", file.name),
        NotificationType.INFORMATION,
      )
      OxcFixAllResult.FixesTurnedOff -> notification(
        "$prefix.fix.all.turned.off.label",
        OxcFrontendBundle.message("$prefix.fix.all.turned.off.description"),
        NotificationType.INFORMATION,
      ).addAction(openSettings(tool, "$prefix.open.settings.link"))
      OxcFixAllResult.ToolNotFound -> notification(
        "$prefix.fix.all.not.found.label",
        OxcFrontendBundle.message("$prefix.fix.all.not.found.description", file.name),
        NotificationType.WARNING,
      ).addAction(openSettings(tool, "$prefix.open.settings.link"))
      OxcFixAllResult.Applied -> notification(
        "$prefix.fix.all.success.label",
        OxcFrontendBundle.message("$prefix.fix.all.success.description"),
        NotificationType.INFORMATION,
      )
      OxcFixAllResult.TimedOut -> notification(
        "$prefix.fix.all.timeout.label",
        OxcFrontendBundle.message("$prefix.fix.all.timeout.description"),
        NotificationType.WARNING,
      )
      is OxcFixAllResult.Failed -> notification(
        "$prefix.fix.all.failure.label",
        OxcFrontendBundle.message("$prefix.fix.all.failure.description", result.message),
        NotificationType.ERROR,
      )
      OxcFixAllResult.Untrusted -> notification(
        "$prefix.fix.all.untrusted.label",
        OxcFrontendBundle.message("$prefix.fix.all.untrusted.description"),
        NotificationType.WARNING,
      ).addAction(NotificationAction.createSimpleExpiring(OxcFrontendBundle.message("trust.project")) {
        trustAndRun(tool, file)
      })
      OxcFixAllResult.UnsupportedFile -> notification(
        "$prefix.file.not.supported.title",
        OxcFrontendBundle.message("$prefix.file.not.supported.description", file.name),
        NotificationType.WARNING,
      ).addAction(openSettings(tool, "$prefix.configure.extensions.link"))
    }
    notification.notify(project)
  }

  /**
   * The backend holds the project's trust, and asks with the platform's dialog. Once trusted, Fix All runs
   * again, because that's what was asked for.
   */
  private fun trustAndRun(tool: OxcTool, file: VirtualFile) {
    scope.launch {
      if (durable { OxcToolApi.getInstance().trustProject(project.projectId()) }) run(tool, file)
    }
  }

  private fun openSettings(tool: OxcTool, textKey: String): NotificationAction =
    NotificationAction.createSimple(OxcFrontendBundle.message(textKey)) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, tool.configurableClass)
    }

  private fun notification(titleKey: String, content: String, type: NotificationType) =
    NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
      .createNotification(OxcFrontendBundle.message(titleKey), content, type)
}

internal val OxcTool.messagePrefix: String
  get() = name.lowercase()

private val OxcTool.configurableClass: Class<out Configurable>
  get() = when (this) {
    OxcTool.OXLINT -> OxlintConfigurable::class.java
    OxcTool.OXFMT -> OxfmtConfigurable::class.java
  }
