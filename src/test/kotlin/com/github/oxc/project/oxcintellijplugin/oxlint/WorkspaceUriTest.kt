package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ServerLaunch
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import org.eclipse.lsp4j.ConfigurationItem

/**
 * The server keys its per-workspace options by the workspace folder URI it received in `initialize`,
 * and echoes that URI back as the `scopeUri` of a `workspace/configuration` request. If the spellings
 * differ, the server silently falls back to its defaults instead of the configured settings.
 */
class WorkspaceUriTest : OxcFixtureTestCase() {

  fun testConfigurationIsKeyedByTheWorkspaceFolderUri() {
    val descriptor = createDescriptor()
    val workspaceUris = descriptor.initializationOptions().map { it["workspaceUri"] }
    val folderUris = descriptor.createInitializeParams().workspaceFolders.map { it.uri }

    assertSize(1, folderUris)
    assertEquals(folderUris, workspaceUris)
  }

  fun testWorkspaceUriDoesNotHaveATrailingSlash() {
    createDescriptor().initializationOptions().forEach {
      assertFalse((it["workspaceUri"] as String).endsWith("/"))
    }
  }

  fun testWorkspaceConfigurationIsFoundByTheWorkspaceUri() {
    val descriptor = createDescriptor()

    descriptor.initializationOptions().forEach {
      val item = ConfigurationItem().apply { scopeUri = it["workspaceUri"] as String }
      assertEquals(it["options"], descriptor.getWorkspaceConfiguration(item))
    }
  }

  fun testWorkspaceConfigurationIsNotFoundForAnotherUri() {
    val item = ConfigurationItem().apply { scopeUri = "file:///not/a/workspace" }

    assertNull(createDescriptor().getWorkspaceConfiguration(item))
  }

  private fun createDescriptor(): ToolLspClientDescriptor {
    val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.tempDirPath)!!
    return ToolLspClientDescriptor(myFixture.project, OxlintDefinition, ServerLaunch("${myFixture.tempDirPath}/oxlint", listOf("--lsp"), root))
  }

  /** One entry for the descriptor's one root, so a test looping over them can't pass by having none. */
  @Suppress("UNCHECKED_CAST")
  private fun ToolLspClientDescriptor.initializationOptions(): List<Map<String, Any?>> =
    (createInitializationOptions() as List<Map<String, Any?>>).also { assertSize(1, it) }
}
