package com.github.oxc.project.oxcintellijplugin.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.OxcServerCommand
import com.github.oxc.project.oxcintellijplugin.OxcTargetRun
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusNotifications
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DiagnosticWorkspaceCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult

class OxlintLspServerDescriptor(
    project: Project,
    private val command: OxcServerCommand,
) : LspServerDescriptor(project, "Oxlint", command.root) {
    private val targetRun: OxcTargetRun = OxcTargetRunBuilder(project)
        .getBuilder(OxlintSettings.getInstance(project).configurationMode, command.executable, command.vitePlus)
        .setWorkingDirectory(command.root.path)
        .addParameters(command.arguments.map { ProcessCommandParameter.Value(it) })
        .build()

    override fun isSupportedFile(file: VirtualFile): Boolean {
        val settings = OxlintSettings.getInstance(project)
        return settings.isEnabled() && settings.fileSupported(file) && command.supports(file, project,
            settings.binarySource, settings.vitePlusPath,
            settings.configurationMode == ConfigurationMode.MANUAL && settings.binaryPath.isNotBlank())
    }

    override fun createCommandLine(): GeneralCommandLine {
        throw RuntimeException("Not expected to be called because startServerProcess() is overridden")
    }

    override fun startServerProcess(): OSProcessHandler {
        try {
            return targetRun.startProcess()
        } catch (e: ExecutionException) {
            if (command.vitePlus) VitePlusNotifications.getInstance(project).launchFailed(command.root.path, presentableName)
            throw e
        }
    }

    override val lspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
            if (command.vitePlus) VitePlusNotifications.getInstance(project).started(command.root.path, presentableName)
        }

        override fun serverStopped(shutdownNormally: Boolean) {
            if (command.vitePlus && !shutdownNormally && !project.isDisposed) {
                VitePlusNotifications.getInstance(project).launchFailed(command.root.path, presentableName)
            }
        }
    }

    override fun getFilePath(file: VirtualFile): String =
        targetRun.toTargetPath(file.path)

    override fun findLocalFileByPath(path: String): VirtualFile? =
        super.findLocalFileByPath(targetRun.toLocalPath(path))

    override fun createInitializationOptions(): Any {
        val initializationOptions = roots.map {
            return@map mapOf(
                "workspaceUri" to getFileUri(it).removeSuffix("/"),
                "options" to createWorkspaceConfig()
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
        if (roots.none { getFileUri(it).removeSuffix("/") == item.scopeUri?.removeSuffix("/") }) return null
        return createWorkspaceConfig()
    }

    override val clientCapabilities: ClientCapabilities
        get() {
            thisLogger().debug("Client Capabilities: ${super.clientCapabilities}")
            return super.clientCapabilities.apply {
                workspace.apply {
                    configuration = true
                    // The server uses pull diagnostics only when the client declares both
                    // textDocument.diagnostic and workspace.diagnostics.refreshSupport. The platform
                    // declares only the former, leaving the server in push mode, where it runs a full
                    // lint (and spawns tsgolint when type-aware is enabled) on every keystroke.
                    // Declaring refreshSupport switches the server to pull mode, where lint runs are
                    // driven by the IDE highlighting daemon instead. See oxc-intellij-plugin#366.
                    diagnostics = DiagnosticWorkspaceCapabilities(true)
                }
            }
        }

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = null

    override val lspHoverSupport = false

    override val lspDiagnosticsSupport: LspDiagnosticsSupport = OxlintLspDiagnosticsSupport()

    private fun createWorkspaceConfig(): Map<String, Any?> {
        val oxlintPackage = OxlintPackage(project)
        val settings = OxlintSettings.getInstance(project)

        return mapOf(
            "configPath" to oxlintPackage.configPath(),
            "disableNestedConfig" to (command.vitePlus || settings.disableNestedConfig),
            "fixKind" to settings.fixKind.toLspValue(),
            // Deprecated flags kept for backward compat with older servers
            "flags" to mapOf(
                "disable_nested_config" to (command.vitePlus || settings.disableNestedConfig).toString(),
                "fix_kind" to settings.fixKind.toLspValue(),
            ),
            // The server's "run" option only decides when diagnostics are pushed, which no longer
            // applies now that the client declares refreshSupport and the server serves pull
            // diagnostics. Omitting it leaves the server at its default.
            "typeAware" to settings.typeAware,
            "unusedDisableDirectives" to settings.state.unusedDisableDirectives.toLspValue(),
        )
    }
}
