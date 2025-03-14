package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsComponent
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.InitializeParams

@Suppress("UnstableApiUsage")
class OxcLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Oxc") {

    companion object {
        fun supportedFile(file: VirtualFile): Boolean {
            return file.isInLocalFileSystem && listOf(
                "astro",
                "js", "jsx", "cjs", "mjs",
                "svelte",
                "ts", "tsx", "cts", "mts",
                "vue"
            ).contains(file.extension)
        }
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        thisLogger().debug(
            "file.path ${file.path}, file.isInLocalFileSystem ${file.isInLocalFileSystem}")
        return supportedFile(file)
    }

    override fun createCommandLine(): GeneralCommandLine {
        val binary = findBinary()
        thisLogger().debug("Creating Oxc command with binary: $binary")
        return GeneralCommandLine(binary)
    }

    override fun createInitializationOptions(): Any {
        val state = project.service<OxcSettingsComponent>().state
        val lspConfig = mapOf(
            "settings" to mapOf(
                "enable" to state.enable,
                "run" to state.runTrigger.toLspValue()
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

    private fun findBinary(): String {
        val configuredBinaryPath = project.service<OxcSettingsComponent>().binaryPath
        if (!configuredBinaryPath.isNullOrBlank()) {
            return FileUtil.toSystemDependentName(
                project.service<PathMacroManager>().expandPath(configuredBinaryPath))
        }

        val binaryName = "oxc_language_server"
        val foundBinaries = roots.map {
            return@map it.findFileByRelativePath("node_modules/.bin/$binaryName")
                       ?: it.findFileByRelativePath("node_modules/.bin/$binaryName.exe")
        }.filterNotNull()
        if (foundBinaries.size == 1) {
            return foundBinaries.single().path
        }
        if (foundBinaries.size > 1) {
            val binary = foundBinaries.first().path
            thisLogger().warn(
                "Found multiple binaries [${foundBinaries.joinToString { it.path }}], using $binary")
            return binary
        }

        thisLogger().warn("Unable to find binaries, falling back to PATH")
        return binaryName
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
