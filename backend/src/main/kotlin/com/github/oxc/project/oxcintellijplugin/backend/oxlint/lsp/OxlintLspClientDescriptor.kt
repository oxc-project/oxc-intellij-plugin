package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.lsp.LspDisabledCustomization
import com.github.oxc.project.oxcintellijplugin.backend.lsp.pathToFileUri
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.isOxlintSupportedFile
import com.github.oxc.project.oxcintellijplugin.backend.process.NodeServerRun
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
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
  root: VirtualFile,
  executable: String,
) : LspClientDescriptor(project, "Oxlint", root) {

  private val serverRun = NodeServerRun(project, executable, listOf("--lsp"), root.path)

  override fun isSupportedFile(file: VirtualFile): Boolean =
    isOxlintSupportedFile(file) && roots.any { VfsUtilCore.isAncestor(it, file, false) }

  override fun createCommandLine(): GeneralCommandLine = throw UnsupportedOperationException(
    "Not expected to be called because startServerProcess() is overridden")

  override fun startServerProcess(): OSProcessHandler = serverRun.startProcess()

  /**
   * Returns paths that work on the target machine based on the configured interpreter. The default
   * implementation only looks at where the project is running, ignoring the interpreter.
   *
   * This runs on the UI thread, so it cannot perform any blocking tasks.
   */
  override fun getFileUri(file: VirtualFile): String =
    if (file.isInLocalFileSystem) pathToFileUri(serverRun.toServerPath(file.path) ?: file.path)
    else super.getFileUri(file)

  override fun findLocalFileByPath(path: String): VirtualFile? =
    super.findLocalFileByPath(serverRun.toLocalPath(path) ?: path)

  override fun createInitializeParams(): InitializeParams = super.createInitializeParams().apply {
    roots.singleOrNull()?.let { root -> serverRun.toServerPath(root.path)?.let { rootPath = it } }
  }

  override fun createInitializationOptions(): Any =
    roots.map { mapOf("workspaceUri" to getFileUri(it), "options" to WORKSPACE_CONFIG) }

  override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? =
    WORKSPACE_CONFIG.takeIf { roots.any { root -> getFileUri(root) == item.scopeUri } }

  override val lspCustomization: LspCustomization = object : LspDisabledCustomization() {
    override val codeActionsCustomizer: LspCodeActionsCustomizer = LspCodeActionsSupport()
    override val diagnosticsCustomizer: LspDiagnosticsCustomizer = OxlintLspDiagnosticsSupport()
  }

  private companion object {

    // TODO: Read these from the project's settings once the plugin has them.
    val WORKSPACE_CONFIG = mapOf(
      "configPath" to null,
      "disableNestedConfig" to false,
      "fixKind" to "safe_fix",
      "typeAware" to false,
      "unusedDisableDirectives" to "allow",
    )
  }
}
