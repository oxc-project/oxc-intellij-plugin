package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.ResourceBundle

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

    fun testSettingsPageIsRegistered() {
        assertTrue(Configurable.PROJECT_CONFIGURABLE.getExtensions(project).any { it.id == "OxlintConfigurable" })
    }

    fun testFixAllIsInTheMenus() {
        val action = ActionManager.getInstance().getAction("OxlintApplyFixAllAction")
        assertNotNull(action)
        for (group in listOf("EditorPopupMenu", "ProjectViewPopupMenu")) {
            val children = (ActionManager.getInstance().getAction(group) as DefaultActionGroup).getChildren(ActionManager.getInstance())
            assertTrue(group, action in children)
        }
    }

    fun testFixOnSaveRunsBeforeFormatOnSave() {
        val names = ExtensionPointName<Any>("com.intellij.actionOnSave").extensionList.map { it.javaClass.simpleName }
        val fix = names.indexOf("OxlintFixAllOnSaveAction")
        assertTrue(names.toString(), fix >= 0)
        val format = names.indexOf("FormatOnSaveAction")
        if (format >= 0) assertTrue(names.toString(), fix < format)
    }

    fun testOnSaveRowIsRegistered() {
        val names = ExtensionPointName<Any>("com.intellij.actionOnSaveInfoProvider").extensionList.map { it.javaClass.simpleName }
        assertContainsElements(names, "OxcOnSaveInfoProvider")
    }

    fun testMessagesWithoutPlaceholdersDoNotHaveDoubledQuotes() {
        for (name in listOf("messages.OxcFrontendBundle", "messages.OxcBackendBundle")) {
            val bundle = ResourceBundle.getBundle(name, Locale.ROOT, bundleLoader(name))
            for (key in bundle.keySet()) {
                val value = bundle.getString(key)
                if ("{" !in value) assertFalse("$key: $value", "''" in value)
            }
        }
    }

    private fun bundleLoader(name: String): ClassLoader =
        (if ("Frontend" in name) OxlintConfigurable::class.java else OxlintSettings::class.java).classLoader

    fun testRegistryKeyIsDefined() {
        assertEquals(30000, Registry.intValue("oxc.lint.fix.all.timeout.ms"))
    }

    fun testFixAllAnswersOverRpc() {
        val projectId = project.projectId()
        val file = myFixture.addFileToProject("notes.txt", "").virtualFile.rpcId()
        val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
            runBlocking { OxcToolApi.getInstance().fixAll(projectId, OxcTool.OXLINT, file) }
        }

        assertEquals(OxcFixAllResult.UnsupportedFile, PlatformTestUtil.waitForFuture(call, 30_000))
    }

    fun testSettingsUpdatesAnswerOverRpc() {
        val projectId = project.projectId()
        val call = ApplicationManager.getApplication().executeOnPooledThread<OxlintSettingsData> {
            runBlocking { OxlintSettingsApi.getInstance().updates(projectId).first() }
        }

        assertEquals(OxlintSettings.getInstance(project).toData(), PlatformTestUtil.waitForFuture(call, 30_000))
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
            runBlocking { OxcToolApi.getInstance().restart(projectId, OxcTool.OXLINT) }
        }

        PlatformTestUtil.waitForFuture(call, 30_000)
    }
}
