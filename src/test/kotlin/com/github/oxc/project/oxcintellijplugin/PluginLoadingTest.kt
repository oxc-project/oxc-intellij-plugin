package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

/**
 * A test IDE is a monolith, so every content module should load and the RPC call stays in process.
 * This does not replace running the plugin in split mode.
 */
class PluginLoadingTest : BasePlatformTestCase() {
    fun testPluginIsLoaded() {
        val pluginIsLoaded = PluginManagerCore.isLoaded(PluginId.getId("com.github.oxc.project.oxcintellijplugin"))

        assertTrue(pluginIsLoaded)
    }

    fun testFrontendModuleIsLoaded() {
        assertNotNull(ActionManager.getInstance().getAction("OxlintRestartServerAction"))
    }

    fun testBackendListsFoldersOverRpc() {
        val projectId = project.projectId()
        val call = ApplicationManager.getApplication().executeOnPooledThread<String> {
            runBlocking { BackendFilesApi.getInstance().listFolder(projectId, "/").path }
        }

        assertNotNull(PlatformTestUtil.waitForFuture(call, 30_000))
    }

    fun testBackendAnswersOverRpc() {
        val projectId = project.projectId()
        val call = ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking { OxlintServerApi.getInstance().restart(projectId) }
        }

        PlatformTestUtil.waitForFuture(call, 30_000)
    }
}
