package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.lsp.pathToFileUri
import com.github.oxc.project.oxcintellijplugin.backend.process.NativeServerRun
import com.github.oxc.project.oxcintellijplugin.backend.process.NodeServerRun
import com.github.oxc.project.oxcintellijplugin.backend.process.ServerRun
import com.github.oxc.project.oxcintellijplugin.backend.process.isNodeScript
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.InitializeParams

class ToolLspClientDescriptor(
  project: Project,
  private val definition: ToolDefinition,
  private val launch: ServerLaunch,
) : LspClientDescriptor(project, definition.displayName, launch.root) {

  private val settings: ToolSettings
    get() = definition.settings(project)

  @Volatile
  private var serverRun: ServerRun? = null

  override fun isSupportedFile(file: VirtualFile): Boolean =
    settings.isSupported(file) && roots.any { VfsUtilCore.isAncestor(it, file, false) }

  override fun createCommandLine(): GeneralCommandLine = throw UnsupportedOperationException(
    "Not expected to be called because startServerProcess() is overridden")

  override fun startServerProcess(): OSProcessHandler {
    val run = createServerRun()
    serverRun = run
    val process = run.startProcess()
    project.service<OutsideConfigWatcher>().watch(definition.tool, configPath()?.fileOutsideWorkspace)
    return process
  }

  /** Automatic resolutions are always scripts, and a manual path is a native binary unless it's one too. */
  private fun createServerRun(): ServerRun {
    val rootPath = launch.root.path
    return if (settings.configurationMode == ConfigurationMode.MANUAL && !isNodeScript(launch.executable, rootPath)) {
      NativeServerRun(launch.executable, launch.parameters, rootPath)
    }
    else {
      NodeServerRun(project, launch.executable, launch.parameters, rootPath)
    }
  }

  /**
   * Returns paths that work on the target machine based on the configured interpreter. The default
   * implementation only looks at where the project is running, ignoring the interpreter.
   *
   * This runs on the UI thread, so it cannot perform any blocking tasks.
   */
  override fun getFileUri(file: VirtualFile): String =
    if (file.isInLocalFileSystem) pathToFileUri(toServerPath(file.path) ?: file.path)
    else super.getFileUri(file)

  override fun findLocalFileByPath(path: String): VirtualFile? =
    super.findLocalFileByPath(toLocalPath(path) ?: path)

  override fun createInitializeParams(): InitializeParams = super.createInitializeParams().apply {
    roots.singleOrNull()?.let { root -> toServerPath(root.path)?.let { rootPath = it } }
  }

  override fun createInitializationOptions(): Any =
    roots.map { mapOf("workspaceUri" to getFileUri(it), "options" to workspaceConfig()) }

  override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? =
    workspaceConfig().takeIf { roots.any { root -> getFileUri(root) == item.scopeUri } }

  private fun workspaceConfig(): Map<String, Any?> =
    definition.workspaceConfig(project, configPath()?.serverValue)

  private fun configPath(): ToolConfigPath? {
    if (settings.configurationMode != ConfigurationMode.MANUAL) return null

    return ToolConfigPath.of(settings.configPath, roots.single().path, ::toServerPath, ::toLocalPath)
  }

  private fun toServerPath(localPath: String): String? = serverRun?.toServerPath(localPath)

  private fun toLocalPath(serverPath: String): String? = serverRun?.toLocalPath(serverPath)

  override val lspCustomization: LspCustomization = definition.lspCustomization(project)

  /** The platform starts its log lines with this, which is how to tell the two servers apart. */
  override fun toString(): String = "${definition.displayName}@${project.name}"
}
