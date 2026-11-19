package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.testutil.resetOxcSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A test IDE is a monolith, so every content module should load and the RPC call stays in process.
 * This does not replace running the plugin in split mode.
 */
class PluginLoadingTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    resetOxcSettings(project)
  }

  fun testPluginIsLoaded() {
    val pluginIsLoaded = PluginManagerCore.isLoaded(
      PluginId.getId("com.github.oxc.project.oxcintellijplugin"))

    assertTrue(pluginIsLoaded)
  }

  fun testSettingsUpdatesAnswerOverRpc() {
    val projectId = project.projectId()
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxlintSettingsData> {
      runBlocking { OxlintSettingsApi.getInstance().updates(projectId).first() }
    }

    assertEquals(OxlintSettings.getInstance(project).toData(),
      PlatformTestUtil.waitForFuture(call, 30_000))
  }
}
