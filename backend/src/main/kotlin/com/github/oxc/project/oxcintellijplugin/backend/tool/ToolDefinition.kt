package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.customization.LspCustomization

/** What differs between the tools. Everything else in this package is shared. */
abstract class ToolDefinition(
  val tool: OxcTool,
  val displayName: String,
  val packageName: String,
  /** The start of the tool's message keys. */
  val messagePrefix: String,
  val configurableId: String,
  val fixAllTimeoutRegistryKey: String,
  /** The subcommand of `vp` that runs this tool, as in `vp lint --lsp`. */
  val vitePlusCommand: String,
) {
  /** The platform tells servers apart by their provider's class, so each tool needs its own. */
  abstract val providerClass: Class<out ToolLspIntegrationProvider>

  abstract fun settings(project: Project): ToolSettings

  /** [configPath] is the value to send to the server, or null to let it look for a config. */
  abstract fun workspaceConfig(project: Project, configPath: String?): Map<String, Any?>

  abstract fun lspCustomization(project: Project): LspCustomization

  abstract fun fixer(project: Project): ToolFixer

  /** Returns null if the tool shouldn't run for the file, or there's nothing to run. */
  fun createDescriptor(project: Project, file: VirtualFile): ToolLspClientDescriptor? {
    val settings = settings(project)
    if (!settings.isEnabled || !settings.isSupported(file)) return null

    return ToolPackage(project, this).resolve(file)?.let { ToolLspClientDescriptor(project, this, it) }
  }

  /** The root of the running server that handles [file], if there is one. */
  fun runningServerRoot(project: Project, file: VirtualFile): VirtualFile? =
    LspClientManager.getInstance(project).getClients(providerClass)
      .firstOrNull { it.descriptor.isSupportedFile(file) }
      ?.descriptor?.roots?.singleOrNull()

  companion object {
    val all: List<ToolDefinition> by lazy { listOf(OxlintDefinition, OxfmtDefinition) }

    fun of(tool: OxcTool): ToolDefinition = all.first { it.tool == tool }
  }
}
