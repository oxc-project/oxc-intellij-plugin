package com.github.iwanabethatguy.oxcintellijplugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport

@Suppress("UnstableApiUsage")
class OxcLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Oxc") {

    companion object {

        fun supportedFile(file: VirtualFile): Boolean {
            return file.isInLocalFileSystem && listOf("js", "jsx", "ts", "tsx").contains(
                file.extension)
        }
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        thisLogger().warn(
            "file.isInLocalFileSystem ${file.isInLocalFileSystem}, file.extension ${file.extension}")
        return supportedFile(file)
    }

    override fun createCommandLine(): GeneralCommandLine {
        thisLogger().warn("Start oxc language server")

        return GeneralCommandLine(findBinary())
    }

    override fun createInitializationOptions(): Any? {
        val options = super.createInitializationOptions()
        thisLogger().warn("get oxc configuration $options")
        return options
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
