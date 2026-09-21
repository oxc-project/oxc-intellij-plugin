package com.github.oxc.project.oxcintellijplugin.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.Diagnostic

class OxlintLspDiagnosticsSupport : LspDiagnosticsSupport() {

    override fun getMessage(diagnostic: Diagnostic): String {
        thisLogger().debug("Creating message for diagnostic: $diagnostic")
        val message = super.getMessage(diagnostic)
        return "${diagnostic.source}: $message ${
            diagnostic.code?.get() ?: OxlintBundle.message("oxlint.diagnostic.unknown.code")
        }"
    }

    override fun getTooltip(diagnostic: Diagnostic): String {
        thisLogger().debug("Creating tooltip for diagnostic: $diagnostic")
        val tooltip = super.getTooltip(diagnostic)
        var rule = diagnostic.code?.get() ?: OxlintBundle.message("oxlint.diagnostic.unknown.code")
        if (diagnostic.codeDescription?.href != null) {
            rule = "<a href=\"${diagnostic.codeDescription.href}\">${rule}</a>"
        }

        return "${diagnostic.source}: $tooltip $rule"
    }
}
