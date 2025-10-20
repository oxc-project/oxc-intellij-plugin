package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcTargetRun
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.settings.OxfmtSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.InitializeParams

class OxfmtLspServerDescriptor(
    project: Project,
    root: VirtualFile,
    executable: String,
) : LspServerDescriptor(project, "Oxfmt", root) {
    private val targetRun: OxcTargetRun = run {
        val builder = OxcTargetRunBuilder(project).getBuilder(executable).setWorkingDirectory(root.path)

        builder.build()
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        thisLogger().debug("file.path ${file.path}")
        return OxfmtSettings.getInstance(project).fileSupported(file) && roots.any { root ->
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

    override val lspFormattingSupport = OxfmtLspFormattingSupport(project)

    override val lspHoverSupport = false

    override val lspDiagnosticsSupport = null

    private fun createWorkspaceConfig(workspace: VirtualFile): Map<String, Any?> {
        val oxfmtPackage = OxfmtPackage(project)
        val settings = OxfmtSettings.getInstance(project)

        return mapOf(
            "configPath" to oxfmtPackage.configPath(),
            "flags" to settings.flags,
            "fmt.experimental" to oxfmtPackage.isEnabled(),
            "fmt.configPath" to oxfmtPackage.configPath(),
        )
    }
}
