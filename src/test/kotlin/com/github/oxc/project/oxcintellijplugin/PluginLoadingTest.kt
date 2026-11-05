package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.shared.ping.OxcPingApi
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.extensions.PluginId
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
        assertNotNull(ActionManager.getInstance().getAction("OxcPingBackendAction"))
    }

    fun testBackendAnswersOverRpc() {
        val result = runBlocking { OxcPingApi.getInstance().ping() }

        assertEquals(ProcessHandle.current().pid(), result.backendProcessId)
    }
}
