package com.github.iwanabethatguy.oxcintellijplugin.lsp

import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.Diagnostic

@Suppress("UnstableApiUsage")
class OxcLspDiagnosticsSupport : LspDiagnosticsSupport() {

    override fun getMessage(diagnostic: Diagnostic): String {
        return "${diagnostic.source}: ${diagnostic.message} ${diagnostic.code.get()}"
    }

    override fun getTooltip(diagnostic: Diagnostic): String {
        var rule = diagnostic.code.get()
        if (diagnostic.codeDescription?.href != null) {
            rule = "<a href=\"${diagnostic.codeDescription.href}\">${diagnostic.code.get()}</a>"
        }

        return "${diagnostic.source}: ${diagnostic.message} $rule"
    }
}
