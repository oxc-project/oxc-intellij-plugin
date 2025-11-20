package com.github.oxc.project.oxcintellijplugin.oxfmt.lsp

import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFormattingSupport

class OxfmtLspFormattingSupport(private val project: Project) : LspFormattingSupport() {

    override fun shouldFormatThisFileExclusivelyByServer(file: VirtualFile,
        ideCanFormatThisFileItself: Boolean,
        serverExplicitlyWantsToFormatThisFile: Boolean): Boolean {
        val shouldFormatFile = OxfmtSettings.getInstance(project).fileSupported(file)
        thisLogger().debug("Should format ${file.path} = $shouldFormatFile")

        return shouldFormatFile
    }
}
