package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.lsp.LspDisabledCustomization
import com.github.oxc.project.oxcintellijplugin.backend.lsp.pathToFileUri
import com.github.oxc.project.oxcintellijplugin.backend.process.NativeServerRun
import com.github.oxc.project.oxcintellijplugin.backend.process.NodeServerRun
import com.github.oxc.project.oxcintellijplugin.backend.process.ServerRun
import com.github.oxc.project.oxcintellijplugin.backend.process.isNodeScript
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCodeActionsCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsCustomizer
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.InitializeParams

class OxlintLspClientDescriptor(
  project: Project,
  private val root: VirtualFile,
  private val executable: String,
) : LspClientDescriptor(project, "Oxlint", root) {

  @Volatile
  private var serverRun: ServerRun? = null

  override fun isSupportedFile(file: VirtualFile): Boolean =
    OxlintSettings.getInstance(project).isSupported(file) && roots.any { VfsUtilCore.isAncestor(it, file, false) }

  override fun createCommandLine(): GeneralCommandLine = throw UnsupportedOperationException(
    "Not expected to be called because startServerProcess() is overridden")

  override fun startServerProcess(): OSProcessHandler {
    val run = createServerRun()
    serverRun = run
    val process = run.startProcess()
    project.service<OxlintOutsideConfigWatcher>().watch(configPath()?.fileOutsideWorkspace)
    return process
  }

  /** Automatic resolutions are always scripts, and a manual path is a native binary unless it's one too. */
  private fun createServerRun(): ServerRun {
    val mode = OxlintSettings.getInstance(project).state.configurationMode
    return if (mode == ConfigurationMode.MANUAL && !isNodeScript(executable, root.path)) {
      NativeServerRun(executable, SERVER_PARAMETERS, root.path)
    }
    else {
      NodeServerRun(project, executable, SERVER_PARAMETERS, root.path)
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

  private fun workspaceConfig(): Map<String, Any?> {
    val state = OxlintSettings.getInstance(project).state
    return mapOf(
      "configPath" to configPath()?.serverValue,
      "disableNestedConfig" to state.disableNestedConfig,
      "fixKind" to state.fixKind.serverValue,
      "typeAware" to state.typeAware,
      "unusedDisableDirectives" to state.unusedDisableDirectives.serverValue,
    )
  }

  private fun configPath(): OxlintConfigPath? {
    val state = OxlintSettings.getInstance(project).state
    if (state.configurationMode != ConfigurationMode.MANUAL) return null

    return OxlintConfigPath.of(state.configPath, roots.single().path, ::toServerPath, ::toLocalPath)
  }

  private fun toServerPath(localPath: String): String? = serverRun?.toServerPath(localPath)

  private fun toLocalPath(serverPath: String): String? = serverRun?.toLocalPath(serverPath)

  override val lspCustomization: LspCustomization = object : LspDisabledCustomization() {
    override val codeActionsCustomizer: LspCodeActionsCustomizer = LspCodeActionsSupport()
    override val diagnosticsCustomizer: LspDiagnosticsCustomizer = OxlintLspDiagnosticsSupport()
  }

  private companion object {

    val SERVER_PARAMETERS = listOf("--lsp")
  }
}
