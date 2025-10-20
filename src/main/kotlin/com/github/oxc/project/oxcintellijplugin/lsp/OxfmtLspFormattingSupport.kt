package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.settings.OxfmtSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFormattingSupport

class OxfmtLspFormattingSupport(private val project: Project) : LspFormattingSupport() {

    override fun shouldFormatThisFileExclusivelyByServer(file: VirtualFile,
        ideCanFormatThisFileItself: Boolean,
        serverExplicitlyWantsToFormatThisFile: Boolean): Boolean {
        return OxfmtSettings.getInstance(project).fileSupported(file)
    }
}
