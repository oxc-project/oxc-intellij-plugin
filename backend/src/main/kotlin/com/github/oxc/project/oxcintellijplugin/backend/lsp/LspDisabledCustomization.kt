package com.github.oxc.project.oxcintellijplugin.backend.lsp

import com.intellij.platform.lsp.api.customization.LspCallHierarchyCustomizer
import com.intellij.platform.lsp.api.customization.LspCallHierarchyDisabled
import com.intellij.platform.lsp.api.customization.LspCodeActionsCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeActionsDisabled
import com.intellij.platform.lsp.api.customization.LspCodeLensCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeLensDisabled
import com.intellij.platform.lsp.api.customization.LspCommandsCustomizer
import com.intellij.platform.lsp.api.customization.LspCommandsDisabled
import com.intellij.platform.lsp.api.customization.LspCompletionCustomizer
import com.intellij.platform.lsp.api.customization.LspCompletionDisabled
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsCustomizer
import com.intellij.platform.lsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.lsp.api.customization.LspDocumentColorCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentColorDisabled
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsDisabled
import com.intellij.platform.lsp.api.customization.LspDocumentLinkCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentLinkDisabled
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.lsp.api.customization.LspFindReferencesCustomizer
import com.intellij.platform.lsp.api.customization.LspFindReferencesDisabled
import com.intellij.platform.lsp.api.customization.LspFoldingRangeCustomizer
import com.intellij.platform.lsp.api.customization.LspFoldingRangeDisabled
import com.intellij.platform.lsp.api.customization.LspFormattingCustomizer
import com.intellij.platform.lsp.api.customization.LspFormattingDisabled
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionCustomizer
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.platform.lsp.api.customization.LspGoToImplementationCustomizer
import com.intellij.platform.lsp.api.customization.LspGoToImplementationDisabled
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionCustomizer
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionDisabled
import com.intellij.platform.lsp.api.customization.LspHoverCustomizer
import com.intellij.platform.lsp.api.customization.LspHoverDisabled
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersCustomizer
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersDisabled
import com.intellij.platform.lsp.api.customization.LspInlayHintCustomizer
import com.intellij.platform.lsp.api.customization.LspInlayHintDisabled
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingCustomizer
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingDisabled
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsCustomizer
import com.intellij.platform.lsp.api.customization.LspOptimizeImportsDisabled
import com.intellij.platform.lsp.api.customization.LspRenameCustomizer
import com.intellij.platform.lsp.api.customization.LspRenameDisabled
import com.intellij.platform.lsp.api.customization.LspSelectionRangeCustomizer
import com.intellij.platform.lsp.api.customization.LspSelectionRangeDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.lsp.api.customization.LspSignatureHelpCustomizer
import com.intellij.platform.lsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.lsp.api.customization.LspTypeHierarchyCustomizer
import com.intellij.platform.lsp.api.customization.LspTypeHierarchyDisabled
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolCustomizer
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolDisabled

/** Turns off every LSP feature, so that a server's customization only lists the ones it uses. */
open class LspDisabledCustomization : LspCustomization() {

  override val callHierarchyCustomizer: LspCallHierarchyCustomizer = LspCallHierarchyDisabled
  override val codeActionsCustomizer: LspCodeActionsCustomizer = LspCodeActionsDisabled
  override val codeLensCustomizer: LspCodeLensCustomizer = LspCodeLensDisabled
  override val commandsCustomizer: LspCommandsCustomizer = LspCommandsDisabled
  override val completionCustomizer: LspCompletionCustomizer = LspCompletionDisabled
  override val diagnosticsCustomizer: LspDiagnosticsCustomizer = LspDiagnosticsDisabled
  override val documentColorCustomizer: LspDocumentColorCustomizer = LspDocumentColorDisabled
  override val documentHighlightsCustomizer: LspDocumentHighlightsCustomizer = LspDocumentHighlightsDisabled
  override val documentLinkCustomizer: LspDocumentLinkCustomizer = LspDocumentLinkDisabled
  override val documentSymbolCustomizer: LspDocumentSymbolCustomizer = LspDocumentSymbolDisabled
  override val findReferencesCustomizer: LspFindReferencesCustomizer = LspFindReferencesDisabled
  override val foldingRangeCustomizer: LspFoldingRangeCustomizer = LspFoldingRangeDisabled
  override val formattingCustomizer: LspFormattingCustomizer = LspFormattingDisabled
  override val goToDefinitionCustomizer: LspGoToDefinitionCustomizer = LspGoToDefinitionDisabled
  override val goToImplementationCustomizer: LspGoToImplementationCustomizer = LspGoToImplementationDisabled
  override val goToTypeDefinitionCustomizer: LspGoToTypeDefinitionCustomizer = LspGoToTypeDefinitionDisabled
  override val hoverCustomizer: LspHoverCustomizer = LspHoverDisabled
  override val inheritanceMarkersCustomizer: LspInheritanceMarkersCustomizer = LspInheritanceMarkersDisabled
  override val inlayHintCustomizer: LspInlayHintCustomizer = LspInlayHintDisabled
  override val onTypeFormattingCustomizer: LspOnTypeFormattingCustomizer = LspOnTypeFormattingDisabled
  override val optimizeImportsCustomizer: LspOptimizeImportsCustomizer = LspOptimizeImportsDisabled
  override val renameCustomizer: LspRenameCustomizer = LspRenameDisabled
  override val selectionRangeCustomizer: LspSelectionRangeCustomizer = LspSelectionRangeDisabled
  override val semanticTokensCustomizer: LspSemanticTokensCustomizer = LspSemanticTokensDisabled
  override val signatureHelpCustomizer: LspSignatureHelpCustomizer = LspSignatureHelpDisabled
  override val typeHierarchyCustomizer: LspTypeHierarchyCustomizer = LspTypeHierarchyDisabled
  override val workspaceSymbolCustomizer: LspWorkspaceSymbolCustomizer = LspWorkspaceSymbolDisabled
}
