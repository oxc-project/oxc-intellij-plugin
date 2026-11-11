package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization

/** What differs between the tools. Everything else in this package is shared. */
abstract class ToolDefinition(
  val tool: OxcTool,
  /** The product's name, which isn't translated. The server's presentable name in the platform's UI. */
  val displayName: @NlsSafe String,
  val packageName: String,
  val configurableId: String,
) {
  /** The platform tells servers apart by their provider's class, so each tool needs its own. */
  abstract val providerClass: Class<out ToolLspIntegrationProvider>

  abstract fun settings(project: Project): ToolSettings

  /** [configPath] is the value to send to the server, or null to let it look for a config. */
  abstract fun workspaceConfig(project: Project, configPath: String?): Map<String, Any?>

  abstract fun lspCustomization(project: Project): LspCustomization

  /** Returns null if the tool shouldn't run for the file, or there's nothing to run. */
  fun createDescriptor(project: Project, file: VirtualFile): ToolLspClientDescriptor? {
    val settings = settings(project)
    if (!settings.isEnabled || !settings.isSupported(file)) return null

    return ToolPackage(project, this).resolve(file)?.let { ToolLspClientDescriptor(project, this, it) }
  }

  companion object {
    val all: List<ToolDefinition> by lazy { listOf(OxlintDefinition) }

    fun of(tool: OxcTool): ToolDefinition = all.first { it.tool == tool }
  }
}
