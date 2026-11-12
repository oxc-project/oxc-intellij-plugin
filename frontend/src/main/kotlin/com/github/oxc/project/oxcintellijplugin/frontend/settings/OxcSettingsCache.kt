@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.isOxfmtSupportedExtension
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.isOxlintSupportedExtension
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The settings as last sent by the backend, for code that has to answer without waiting for it, such
 * as a menu deciding whether to show an action. Each is null until it first arrives.
 */
@Service(Service.Level.PROJECT)
class OxcSettingsCache(project: Project, scope: CoroutineScope) {

  @Volatile
  var oxlint: OxlintSettingsData? = null
    private set

  @Volatile
  var oxfmt: OxfmtSettingsData? = null
    private set

  init {
    if (!project.isDefault) {
      scope.launch {
        durable { OxlintSettingsApi.getInstance().updates(project.projectId()).collect { oxlint = it } }
      }
      scope.launch {
        durable { OxfmtSettingsApi.getInstance().updates(project.projectId()).collect { oxfmt = it } }
      }
    }
  }

  fun isEnabled(tool: OxcTool): Boolean = when (tool) {
    OxcTool.OXLINT -> oxlint?.configurationMode
    OxcTool.OXFMT -> oxfmt?.configurationMode
  }.let { it != null && it != ConfigurationMode.DISABLED }

  fun isEnabledFor(tool: OxcTool, file: VirtualFile): Boolean = isEnabled(tool) && when (tool) {
    OxcTool.OXLINT -> oxlint?.let { isOxlintSupportedExtension(it.supportedExtensions, file.extension) }
    OxcTool.OXFMT -> oxfmt?.let { isOxfmtSupportedExtension(it.supportedExtensions, file.extension) }
  } == true
}

internal class OxcSettingsCacheStarter : ProjectActivity {

  override suspend fun execute(project: Project) {
    project.service<OxcSettingsCache>()
  }
}
