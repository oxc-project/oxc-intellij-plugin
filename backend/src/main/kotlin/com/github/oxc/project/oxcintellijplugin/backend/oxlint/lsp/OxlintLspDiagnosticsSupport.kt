package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.psi.PsiDocumentManager
import org.eclipse.lsp4j.Diagnostic

class OxlintLspDiagnosticsSupport : LspDiagnosticsSupport() {

  /**
   * Some rules, such as `@stylistic/semi` and `@stylistic/comma-dangle`, report the line break after the
   * code, from the end of a line to the start of the next. A highlight over only a line break has no width,
   * so it would show nowhere in the editor. An empty range at the line's end is shown after the line instead.
   */
  override fun createAnnotation(
    holder: AnnotationHolder,
    diagnostic: Diagnostic,
    textRange: TextRange,
    quickFixes: List<IntentionAction>,
  ) {
    val file = holder.currentAnnotationSession.file
    val text = PsiDocumentManager.getInstance(file.project).getDocument(file)?.immutableCharSequence
    val range = if (text != null && coversOnlyLineBreaks(text, textRange)) TextRange.from(textRange.startOffset, 0) else textRange
    super.createAnnotation(holder, diagnostic, range, quickFixes)
  }

  override fun getMessage(diagnostic: Diagnostic): String =
    "${diagnostic.source}: ${super.getMessage(diagnostic)} ${ruleName(diagnostic)}"

  override fun getTooltip(diagnostic: Diagnostic): String {
    val rule = ruleName(diagnostic)
    val ruleText = diagnostic.codeDescription?.href?.let { "<a href=\"$it\">$rule</a>" } ?: rule

    return "${diagnostic.source}: ${super.getTooltip(diagnostic)} $ruleText"
  }

  private fun ruleName(diagnostic: Diagnostic): String =
    diagnostic.code?.get()?.toString() ?: OxlintBundle.message("oxlint.diagnostic.unknown.code")

  private fun coversOnlyLineBreaks(text: CharSequence, range: TextRange): Boolean =
    !range.isEmpty && range.endOffset <= text.length &&
      (range.startOffset until range.endOffset).all { text[it] == '\n' || text[it] == '\r' }
}
