package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.Diagnostic

class OxlintLspDiagnosticsSupport : LspDiagnosticsSupport() {

    override fun getMessage(diagnostic: Diagnostic): String {
        thisLogger().debug("Creating message for diagnostic: $diagnostic")
        return "${diagnostic.source}: ${diagnostic.message} ${
            diagnostic.code?.get() ?: OxcBundle.message("oxlint.diagnostic.unknown.code")
        }"
    }

    override fun getTooltip(diagnostic: Diagnostic): String {
        thisLogger().debug("Creating tooltip for diagnostic: $diagnostic")
        var rule = diagnostic.code?.get() ?: OxcBundle.message("oxlint.diagnostic.unknown.code")
        if (diagnostic.codeDescription?.href != null) {
            rule = "<a href=\"${diagnostic.codeDescription.href}\">${rule}</a>"
        }

        return "${diagnostic.source}: ${diagnostic.message} $rule"
    }
}
