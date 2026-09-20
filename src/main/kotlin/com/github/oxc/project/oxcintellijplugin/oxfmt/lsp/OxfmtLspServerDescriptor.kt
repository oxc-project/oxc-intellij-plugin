package com.github.oxc.project.oxcintellijplugin.oxfmt.lsp

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.OxcServerCommand
import com.github.oxc.project.oxcintellijplugin.OxcTargetRun
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusNotifications
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerListener
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult

class OxfmtLspServerDescriptor(
    project: Project,
    private val command: OxcServerCommand,
) : LspServerDescriptor(project, "Oxfmt", command.root) {
    private val targetRun: OxcTargetRun = OxcTargetRunBuilder(project)
        .getBuilder(OxfmtSettings.getInstance(project).configurationMode, command.executable, command.vitePlus)
        .setWorkingDirectory(command.root.path)
        .addParameters(command.arguments.map { ProcessCommandParameter.Value(it) })
        .build()

    override fun isSupportedFile(file: VirtualFile): Boolean {
        val settings = OxfmtSettings.getInstance(project)
        return settings.isEnabled() && settings.fileSupported(file) && command.supports(file, project,
            settings.binarySource, settings.vitePlusPath,
            settings.configurationMode == ConfigurationMode.MANUAL && settings.binaryPath.isNotBlank())
    }

    override fun createCommandLine(): GeneralCommandLine {
        throw RuntimeException(
            "Not expected to be called because startServerProcess() is overridden")
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

    override fun getFilePath(file: VirtualFile): String = targetRun.toTargetPath(file.path)

    override fun findLocalFileByPath(path: String): VirtualFile? =
        super.findLocalFileByPath(targetRun.toLocalPath(path))

    override fun createInitializationOptions(): Any {
        val initializationOptions = roots.map {
            return@map mapOf("workspaceUri" to getFileUri(it).removeSuffix("/"),
                "options" to createWorkspaceConfig())
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
                }
            }
        }

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = OxfmtLspFormattingSupport(project)

    override val lspHoverSupport = false

    override val lspDiagnosticsSupport = null

    private fun createWorkspaceConfig(): Map<String, Any?> {
        val oxfmtPackage = OxfmtPackage(project)

        return mapOf(
            "configPath" to null,
            "flags" to emptyMap<String, Any?>(),
            "fmt.experimental" to true,
            "fmt.configPath" to oxfmtPackage.configPath(),
            "fmt.disableNestedConfig" to (command.vitePlus || OxfmtSettings.getInstance(project).disableNestedConfig),
            "run" to "onSave",
            "typeAware" to false,
            "unusedDisableDirectives" to false,
        )
    }
}
