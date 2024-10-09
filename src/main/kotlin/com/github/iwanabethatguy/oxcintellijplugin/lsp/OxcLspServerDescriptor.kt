package com.github.iwanabethatguy.oxcintellijplugin.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.util.FileSearchUtil
import java.time.Duration

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
        val foundBinaries = roots.map {
            val fileQuery = FileSearchUtil.findFileRecursively(it, "oxc_language_server", 10,
                Duration.ofSeconds(5).toMillis())
            return@map fileQuery.findFirst()
        }.filterNotNull()
        if (foundBinaries.size == 1) {
            return foundBinaries.single().path
        }
        if (foundBinaries.size > 1) {
            thisLogger().warn("Found multiple binaries")
            return foundBinaries.single().path
        }

        thisLogger().warn("Unable to find binaries, falling back to PATH")
        return "oxc_language_server"
    }

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = null

    override val lspHoverSupport = false

}
