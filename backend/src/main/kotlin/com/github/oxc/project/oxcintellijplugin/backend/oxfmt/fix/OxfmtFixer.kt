package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.fix

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolFixer
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.util.applyTextEdits
import com.intellij.platform.lsp.util.getRangeInDocument
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.TextEdit

/** Asks the server to format the file and applies the result. */
class OxfmtFixer(private val project: Project) : ToolFixer {

  override suspend fun fix(client: LspClient, file: VirtualFile, document: Document, timeoutMs: Long): Boolean {
    val (stamp, options) = readAction {
      val indent = CodeStyle.getSettings(project, file).getIndentOptions(file.fileType)
      document.modificationStamp to FormattingOptions(indent.INDENT_SIZE, !indent.USE_TAB_CHARACTER)
    }
    val params = DocumentFormattingParams(client.getDocumentIdentifier(file), options)
    val results = withTimeout(timeoutMs) { client.sendRequest { it.textDocumentService.formatting(params) } }

    var applied = false
    WriteCommandAction.runWriteCommandAction(project, OxcBackendBundle.message("oxfmt.run.quickfix"), "Oxc", {
      // The edits are positions in the text that the server saw.
      if (document.modificationStamp != stamp) {
        thisLogger().info("Not formatting ${file.path}, because it changed while waiting for the server")
        return@runWriteCommandAction
      }
      val edits = results.orEmpty().filterNot { isUnchanged(document, it) }
      if (edits.isNotEmpty()) applied = applyTextEdits(document, edits)
    })
    return applied
  }

  /** A document always uses `\n`, so a config asking for `\r\n` would otherwise always have an edit. */
  private fun isUnchanged(document: Document, edit: TextEdit): Boolean {
    val range = getRangeInDocument(document, edit.range) ?: return false
    return document.getText(range) == StringUtil.convertLineSeparators(edit.newText)
  }
}
