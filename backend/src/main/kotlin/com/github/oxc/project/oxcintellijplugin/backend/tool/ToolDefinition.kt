package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.util.PathUtil

/** What differs between the tools. Everything else in this package is shared. */
abstract class ToolDefinition(
  val tool: OxcTool,
  /** The product's name, which isn't translated. The server's presentable name in the platform's UI. */
  val displayName: @NlsSafe String,
  val packageName: String,
  /** The start of the tool's message keys. */
  val messagePrefix: String,
  val configurableId: String,
  val fixAllTimeoutRegistryKey: String,
  /** The subcommand of `vp` that runs this tool, as in `vp lint --lsp`. */
  val vitePlusCommand: String,
  /** The config names that the tool's JSON schema describes. */
  val jsonConfigNames: List<String>,
  /** Every config name that the tool looks for. `vite.config.*` isn't one, because it's Vite's file. */
  val configNames: List<String>,
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

  /** A config file of the tool's, by its name or because it's the manual config path. */
  fun isConfigFile(project: Project?, file: VirtualFile): Boolean =
    file.name in configNames || project != null && isManualConfig(project, file)

  /** A config file that the tool's JSON schema describes. */
  fun isJsonConfigFile(project: Project, file: VirtualFile): Boolean =
    file.name in jsonConfigNames || isManualConfig(project, file)

  /**
   * The server resolves a relative path from its own folder, which depends on the file being checked, so
   * this tries the project's folder and its content roots instead.
   */
  private fun isManualConfig(project: Project, file: VirtualFile): Boolean {
    if (project.isDefault) return false
    val settings = settings(project)
    if (settings.configurationMode != ConfigurationMode.MANUAL) return false
    val path = settings.configPath?.trim()?.takeIf { it.isNotEmpty() }
                 ?.let { PathUtil.toSystemIndependentName(resolveSettingPath(project, it)) } ?: return false
    // FileUtil is obsolete, but nothing public compares or canonicalizes path strings without NIO, which misreads another OS's.
    if (OSAgnosticPathUtil.isAbsolute(path)) return FileUtil.pathsEqual(path, file.path)

    val roots = ProjectRootManager.getInstance(project).contentRoots.asSequence() + listOfNotNull(project.guessProjectDir())
    return roots.any { FileUtil.pathsEqual(FileUtil.toCanonicalPath("${it.path}/$path"), file.path) }
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
