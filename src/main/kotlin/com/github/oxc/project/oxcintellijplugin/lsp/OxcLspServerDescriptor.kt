package com.github.oxc.project.oxcintellijplugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.InitializeParams

@Suppress("UnstableApiUsage")
class OxcLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Oxc") {

    companion object {

        fun supportedFile(file: VirtualFile): Boolean {
            return file.isInLocalFileSystem && listOf("js", "jsx", "ts", "tsx").contains(
                file.extension)
        }
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        thisLogger().debug(
            "file.path ${file.path}, file.isInLocalFileSystem ${file.isInLocalFileSystem}")
        return supportedFile(file)
    }

    override fun createCommandLine(): GeneralCommandLine {
        thisLogger().debug("Start oxc language server")

        return GeneralCommandLine(findBinary())
    }

    override fun createInitializeParams(): InitializeParams {
        val params = super.createInitializeParams()
        thisLogger().debug("Initialization params: $params")
        return params
    }

    private fun findBinary(): String {
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

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = null

    override val lspHoverSupport = false

    override val lspDiagnosticsSupport: LspDiagnosticsSupport? = OxcLspDiagnosticsSupport()

}
