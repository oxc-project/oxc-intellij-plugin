package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import org.eclipse.lsp4j.ConfigurationItem

/**
 * The server keys its per-workspace options by the workspace folder URI it received in
 * `initialize`, and echoes that URI back as the `scopeUri` of a `workspace/configuration`
 * request. A configuration entry whose `workspaceUri` differs from the workspace folder URI,
 * or a lookup that compares against a different spelling of it, silently falls back to the
 * server defaults instead of the configured settings.
 */
class WorkspaceUriTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        // MANUAL mode keeps the descriptor from requiring a Node interpreter; the executable is
        // never spawned by this test.
        OxlintSettings.getInstance(myFixture.project).configurationMode = ConfigurationMode.MANUAL
        OxfmtSettings.getInstance(myFixture.project).configurationMode = ConfigurationMode.MANUAL
    }

    fun testOxlintKeysConfigurationByTheWorkspaceFolderUri() {
        val descriptor = OxlintLspServerDescriptor(myFixture.project, root(), executable(),
            emptyList())
        assertInitializationOptionsMatchesInitializationParams(descriptor)
        assertInitializationOptionsWorkspaceUriDoesNotHaveATrailingSlash(descriptor)
        assertWorkspaceConfigurationOptionsMatchesInitializationParams(descriptor)
    }

    fun testOxfmtKeysConfigurationByTheWorkspaceFolderUri() {
        val descriptor = OxfmtLspServerDescriptor(myFixture.project, root(), executable(),
            emptyList())
        assertInitializationOptionsMatchesInitializationParams(descriptor)
        assertInitializationOptionsWorkspaceUriDoesNotHaveATrailingSlash(descriptor)
        assertWorkspaceConfigurationOptionsMatchesInitializationParams(descriptor)
    }

    private fun assertInitializationOptionsMatchesInitializationParams(
        descriptor: LspServerDescriptor) {
        @Suppress(
            "UNCHECKED_CAST") val initOptions = descriptor.createInitializationOptions() as List<Map<String, Any?>>
        val initParams = descriptor.createInitializeParams()

        assertEquals(initOptions.size, initParams.workspaceFolders.size)
        initOptions.forEachIndexed { index, options ->
            assertEquals(options["workspaceUri"], initParams.workspaceFolders[index].uri)
        }
    }

    private fun assertWorkspaceConfigurationOptionsMatchesInitializationParams(
        descriptor: LspServerDescriptor) {
        @Suppress(
            "UNCHECKED_CAST") val initOptions = descriptor.createInitializationOptions() as List<Map<String, Any?>>
        val initParams = descriptor.createInitializeParams()

        assertEquals(initOptions.size, initParams.workspaceFolders.size)
        initOptions.forEach { options ->
            val configurationItem = ConfigurationItem().apply {
                scopeUri = options["workspaceUri"] as String
            }
            assertEquals(options["options"],
                descriptor.getWorkspaceConfiguration(configurationItem))
        }
    }

    private fun assertInitializationOptionsWorkspaceUriDoesNotHaveATrailingSlash(
        descriptor: LspServerDescriptor) {
        @Suppress(
            "UNCHECKED_CAST") val initOptions = descriptor.createInitializationOptions() as List<Map<String, Any?>>

        initOptions.forEach {
            assertFalse((it["workspaceUri"] as String).endsWith("/"))
        }
    }

    private fun root(): VirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.tempDirPath)!!

    private fun executable(): String = "${myFixture.tempDirPath}/oxc-language-server"
}
