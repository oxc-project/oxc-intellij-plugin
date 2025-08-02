package com.github.oxc.project.oxcintellijplugin

enum class OxlintUnusedDisableDirectivesSeverity {
    ALLOW,
    WARN,
    DENY;

    fun toLspValue(): String {
        return when (this) {
            ALLOW -> "allow"
            WARN -> "warn"
            DENY -> "deny"
        }
    }
}
