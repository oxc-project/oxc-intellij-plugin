package com.github.iwanabethatguy.oxcintellijplugin.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport

class OxcLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Oxc") {

    override fun isSupportedFile(file: VirtualFile):  Boolean {
        thisLogger().warn("file.extension " + file.extension.toString())
        return file.extension == "js" ||  file.extension == "jsx" || file.extension == "ts" || file.extension == "tsx"
    }

    override fun createCommandLine(): GeneralCommandLine {
        thisLogger().warn("Start oxc language server")
        return GeneralCommandLine("oxc_language_server").apply {
        }
    }
    

    override val lspGoToDefinitionSupport = false

    override val lspCompletionSupport = null

    override val lspFormattingSupport = null

    override val lspHoverSupport = false


}