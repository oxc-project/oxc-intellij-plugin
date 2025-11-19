package com.github.oxc.project.oxcintellijplugin.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.OxcTargetRun
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.InitializeParams

class OxlintLspServerDescriptor(
    project: Project,
    root: VirtualFile,
    executable: String,
    executableParameters: List<ProcessCommandParameter>,
) : LspServerDescriptor(project, "Oxlint", root) {
    private val targetRun: OxcTargetRun = run {
        val builder = OxcTargetRunBuilder(project).getBuilder(executable).setWorkingDirectory(root.path).addParameters(executableParameters)

        builder.build()
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        thisLogger().debug("file.path ${file.path}")
        return OxlintSettings.getInstance(project).fileSupported(file) && roots.any { root ->
            file.toNioPath().startsWith(root.toNioPath())
        }
    }

    override fun createCommandLine(): GeneralCommandLine {
        throw RuntimeException("Not expected to be called because startServerProcess() is overridden")
    }

    override fun startServerProcess(): OSProcessHandler =
        targetRun.startProcess()

    override fun getFilePath(file: VirtualFile): String =
        targetRun.toTargetPath(file.path)

    override fun findLocalFileByPath(path: String): VirtualFile? =
        super.findLocalFileByPath(targetRun.toLocalPath(path))

    override fun createInitializationOptions(): Any {
        val initializationOptions = roots.map {
            return@map mapOf(
                "workspaceUri" to it.toNioPath().toUri().toString(),
                "options" to createWorkspaceConfig(it)
            )
        }
        thisLogger().debug("Initialization options: $initializationOptions")
        return initializationOptions
    }

    override fun createInitializeParams(): InitializeParams {
        val params = super.createInitializeParams()
        thisLogger().debug("Initialization params: $params")
        return params
    }

    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? {
        val myRoot = roots.find {
            return@find it.toNioPath().toUri().toString() == item.scopeUri
        } ?: return null
        return createWorkspaceConfig(myRoot)
    }

    override val clientCapabilities: ClientCapabilities
        get() {
            thisLogger().debug("Client Capabilities: ${super.clientCapabilities}")
            return super.clientCapabilities.apply {
                workspace.apply {
                    configuration = true
                }
            }
        }

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = null

    override val lspHoverSupport = false

    override val lspDiagnosticsSupport: LspDiagnosticsSupport = OxlintLspDiagnosticsSupport()

    private fun createWorkspaceConfig(workspace: VirtualFile): Map<String, Any?> {
        val oxlintPackage = OxlintPackage(project)
        val settings = OxlintSettings.getInstance(project)

        return mapOf(
            "configPath" to oxlintPackage.configPath(),
            "flags" to mapOf(
                "disable_nested_config" to settings.disableNestedConfig.toString(),
                "fix_kind" to settings.fixKind.toLspValue(),
            ),
            "run" to settings.state.runTrigger.toLspValue(),
            "typeAware" to settings.typeAware,
            "unusedDisableDirectives" to settings.state.unusedDisableDirectives.toLspValue(),
        )
    }
}
