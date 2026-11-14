package com.github.oxc.project.oxcintellijplugin

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A test IDE is a monolith, so every content module should load and the RPC call stays in process.
 * This does not replace running the plugin in split mode.
 */
class PluginLoadingTest : BasePlatformTestCase() {

    fun testPluginIsLoaded() {
        val pluginIsLoaded = PluginManagerCore.isLoaded(PluginId.getId("com.github.oxc.project.oxcintellijplugin"))

        assertTrue(pluginIsLoaded)
    }
}
