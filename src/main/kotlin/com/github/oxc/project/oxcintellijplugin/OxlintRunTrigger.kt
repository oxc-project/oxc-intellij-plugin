package com.github.oxc.project.oxcintellijplugin

enum class OxlintRunTrigger {
    ON_SAVE,
    ON_TYPE;

    fun toLspValue(): String {
        return when (this) {
            ON_SAVE -> "onSave"
            ON_TYPE -> "onType"
        }
    }
}
