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
        assertConfigurationIsKeyedByWorkspaceFolderUri(
            OxlintLspServerDescriptor(myFixture.project, root(), executable(), emptyList()))
    }

    fun testOxfmtKeysConfigurationByTheWorkspaceFolderUri() {
        assertConfigurationIsKeyedByWorkspaceFolderUri(
            OxfmtLspServerDescriptor(myFixture.project, root(), executable(), emptyList()))
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertConfigurationIsKeyedByWorkspaceFolderUri(descriptor: LspServerDescriptor) {
        val entry = (descriptor.createInitializationOptions() as List<Map<String, Any?>>).single()
        val workspaceUri = entry["workspaceUri"] as String

        assertEquals(listOf(workspaceUri),
            descriptor.createInitializeParams().workspaceFolders.map { it.uri })

        // The platform never appends a trailing slash to a root URI, so the lookup must not
        // expect one either.
        assertFalse(workspaceUri.endsWith("/"))

        val item = ConfigurationItem().apply { scopeUri = workspaceUri }
        assertEquals(entry["options"], descriptor.getWorkspaceConfiguration(item))
    }

    private fun root(): VirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.tempDirPath)!!

    private fun executable(): String = "${myFixture.tempDirPath}/oxc-language-server"
}
