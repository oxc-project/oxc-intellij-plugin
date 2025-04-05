package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcTargetRun
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.InitializeParams
import kotlin.io.path.Path

@Suppress("UnstableApiUsage")
class OxcLspServerDescriptor(
    project: Project,
    root: VirtualFile,
    executable: String,
    private val configPath: String?,
) : LspServerDescriptor(project, "Oxc", root) {
    private val targetRun: OxcTargetRun = run {
        var builder = OxcTargetRunBuilder(project).getBuilder(executable).setWorkingDirectory(root.path)

        if (configPath != null) {
            builder = builder.addParameters(
                listOf(
                    ProcessCommandParameter.Value("--config"),
                    ProcessCommandParameter.FilePath(Path(configPath))
                )
            )
        }

        builder.build()
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        thisLogger().debug("file.path ${file.path}")
        return OxcSettings.getInstance(project).fileSupported(file) && roots.any { root ->
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
        val settings = OxcSettings.getInstance(project)
        val lspConfig = mapOf(
            "settings" to mapOf(
                "enable" to settings.isEnabled(),
                "run" to settings.state.runTrigger.toLspValue()
            )
        )
        thisLogger().debug("Initialization options: $lspConfig")
        return lspConfig
    }

    override fun createInitializeParams(): InitializeParams {
        val params = super.createInitializeParams()
        thisLogger().debug("Initialization params: $params")
        return params
    }

    override val clientCapabilities: ClientCapabilities
        get() {
            thisLogger().debug("Client Capabilities: ${super.clientCapabilities}")
            return super.clientCapabilities
        }

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = null

    override val lspHoverSupport = false

    override val lspDiagnosticsSupport: LspDiagnosticsSupport? = OxcLspDiagnosticsSupport()

}
