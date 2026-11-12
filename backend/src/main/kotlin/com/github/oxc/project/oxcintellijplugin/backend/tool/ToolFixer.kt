package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient

/** How a tool changes a file. [OxcFixAll] handles everything around this. */
interface ToolFixer {

  /** A reason not to ask the server for anything, or null to go ahead. */
  fun refusal(): OxcFixAllResult? = null

  /** Changes the document, leaving it unsaved. Returns false if there was nothing to do. */
  suspend fun fix(client: LspClient, file: VirtualFile, document: Document, timeoutMs: Long): Boolean
}
