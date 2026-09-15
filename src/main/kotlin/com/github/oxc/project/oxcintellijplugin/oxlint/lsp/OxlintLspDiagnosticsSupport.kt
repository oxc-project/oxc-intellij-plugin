package com.github.oxc.project.oxcintellijplugin.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import org.eclipse.lsp4j.Diagnostic

class OxlintLspDiagnosticsSupport : LspDiagnosticsSupport() {

    override fun getMessage(diagnostic: Diagnostic): String {
        thisLogger().debug("Creating message for diagnostic: $diagnostic")
        val message = getMessageWithReflection(diagnostic)
        return "${diagnostic.source}: $message ${
            diagnostic.code?.get() ?: OxlintBundle.message("oxlint.diagnostic.unknown.code")
        }"
    }

    override fun getTooltip(diagnostic: Diagnostic): String {
        thisLogger().debug("Creating tooltip for diagnostic: $diagnostic")
        var rule = diagnostic.code?.get() ?: OxlintBundle.message("oxlint.diagnostic.unknown.code")
        val message = getMessageWithReflection(diagnostic)
        if (diagnostic.codeDescription?.href != null) {
            rule = "<a href=\"${diagnostic.codeDescription.href}\">${rule}</a>"
        }

        return "${diagnostic.source}: $message $rule"
    }

    private fun getMessageWithReflection(diagnostic: Diagnostic): String {
        return try {
            val getMessageMethod = Diagnostic::class.java.getDeclaredMethod("getMessage")
            val messageValue: Any = getMessageMethod.invoke(diagnostic) ?: return ""

            if (messageValue is String) {
                return messageValue
            }

            if (messageValue.javaClass.name == "org.eclipse.lsp4j.jsonrpc.messages.Either") {
                val isLeftMethod = messageValue.javaClass.getDeclaredMethod("isLeft")
                val isLeft = isLeftMethod.invoke(messageValue) as? Boolean ?: false
                if (isLeft) {
                    val getLeftMethod = messageValue.javaClass.getDeclaredMethod("getLeft")
                    return getLeftMethod.invoke(messageValue) as? String? ?: ""
                } else {
                    val getRightMethod = messageValue.javaClass.getDeclaredMethod("getRight")
                    val rightValue = getRightMethod.invoke(messageValue) as? String? ?: return ""
                    val getMarkupValue = rightValue.javaClass.getDeclaredMethod("getValue")
                    return getMarkupValue.invoke(messageValue) as? String? ?: ""
                }
            }

            return messageValue.toString()
        } catch (e: Exception) {
            ""
        }

    }
}
