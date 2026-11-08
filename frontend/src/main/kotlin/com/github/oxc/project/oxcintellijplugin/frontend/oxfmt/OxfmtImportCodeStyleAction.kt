@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxfmt

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.OxcIcons
import com.github.oxc.project.oxcintellijplugin.frontend.settings.OxcSettingsCache
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
import com.intellij.ide.vfs.rpcId
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shown for any file that Oxfmt handles, and for folders. Only the backend can look for the config, and
 * a menu can't wait for it, so an answer that there's no config comes after clicking.
 */
internal class OxfmtImportCodeStyleAction : AnAction(), DumbAware {

  init {
    templatePresentation.icon = OxcIcons.OxcRound
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val cache = e.project?.service<OxcSettingsCache>()
    e.presentation.isEnabledAndVisible = file != null && cache != null &&
                                         (if (file.isDirectory) cache.isEnabled(OxcTool.OXFMT) else cache.isEnabledFor(OxcTool.OXFMT, file))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    project.service<OxfmtCodeStyleImporter>().import(file)
  }
}

@Service(Service.Level.PROJECT)
internal class OxfmtCodeStyleImporter(private val project: Project, private val scope: CoroutineScope) {

  fun import(file: VirtualFile) {
    val fileId = file.rpcId()
    val name = file.name
    scope.launch {
      val result = durable { OxfmtCodeStyleApi.getInstance().importCodeStyle(project.projectId(), fileId) }
      val group = NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
      when (result) {
        OxfmtImportResult.IMPORTED -> group.createNotification(
          OxcFrontendBundle.message("oxfmt.import.code.style.imported"), NotificationType.INFORMATION)
        OxfmtImportResult.ALREADY_IMPORTED -> group.createNotification(
          OxcFrontendBundle.message("oxfmt.import.code.style.already.imported"), NotificationType.INFORMATION)
        OxfmtImportResult.CONFIG_NOT_FOUND -> group.createNotification(
          OxcFrontendBundle.message("oxfmt.configuration.file.not.found.title"),
          OxcFrontendBundle.message("oxfmt.configuration.file.not.found.description", name),
          NotificationType.INFORMATION)
        OxfmtImportResult.CONFIG_NOT_READABLE -> group.createNotification(
          OxcFrontendBundle.message("oxfmt.import.code.style.not.readable.title"),
          OxcFrontendBundle.message("oxfmt.import.code.style.not.readable.description"),
          NotificationType.INFORMATION)
      }.notify(project)
    }
  }
}
