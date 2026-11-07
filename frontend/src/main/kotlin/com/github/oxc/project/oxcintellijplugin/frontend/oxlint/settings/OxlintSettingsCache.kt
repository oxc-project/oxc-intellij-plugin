@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The settings as last sent by the backend, for code that has to answer without waiting for it, such
 * as a menu deciding whether to show an action. It's null until they first arrive.
 */
@Service(Service.Level.PROJECT)
class OxlintSettingsCache(project: Project, scope: CoroutineScope) {

  @Volatile
  var current: OxlintSettingsData? = null
    private set

  init {
    if (!project.isDefault) {
      scope.launch {
        durable {
          OxlintSettingsApi.getInstance().updates(project.projectId()).collect { current = it }
        }
      }
    }
  }
}

internal class OxlintSettingsCacheStarter : ProjectActivity {

  override suspend fun execute(project: Project) {
    project.service<OxlintSettingsCache>()
  }
}
