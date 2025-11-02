package com.github.oxc.project.oxcintellijplugin

enum class OxlintFixKind {
    SAFE_FIX,
    SAFE_FIX_OR_SUGGESTION,
    DANGEROUS_FIX,
    DANGEROUS_FIX_OR_SUGGESTION,
    NONE,
    ALL;

    fun toLspValue(): String {
        return when (this) {
            SAFE_FIX -> "safe_fix"
            SAFE_FIX_OR_SUGGESTION -> "safe_fix_or_suggestion"
            DANGEROUS_FIX -> "dangerous_fix"
            DANGEROUS_FIX_OR_SUGGESTION -> "dangerous_fix_or_suggestion"
            NONE -> "none"
            ALL -> "all"
        }
    }
}
