package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFormattingSupport

/** Reformat Code uses Oxfmt instead of the IDE's formatter for every supported file. */
class OxfmtLspFormattingSupport(private val project: Project) : LspFormattingSupport() {

  override fun shouldFormatThisFileExclusivelyByServer(
    file: VirtualFile,
    ideCanFormatThisFileItself: Boolean,
    serverExplicitlyWantsToFormatThisFile: Boolean,
  ): Boolean = OxfmtSettings.getInstance(project).isSupported(file)
}
