package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintBundle
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.Diagnostic

class OxlintLspDiagnosticsSupport : LspDiagnosticsSupport() {

  override fun getMessage(diagnostic: Diagnostic): String =
    "${diagnostic.source}: ${super.getMessage(diagnostic)} ${ruleName(diagnostic)}"

  override fun getTooltip(diagnostic: Diagnostic): String {
    val rule = ruleName(diagnostic)
    val ruleText = diagnostic.codeDescription?.href?.let { "<a href=\"$it\">$rule</a>" } ?: rule

    return "${diagnostic.source}: ${super.getTooltip(diagnostic)} $ruleText"
  }

  private fun ruleName(diagnostic: Diagnostic): String =
    diagnostic.code?.get()?.toString() ?: OxlintBundle.message("oxlint.diagnostic.unknown.code")
}
