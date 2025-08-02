package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxlintRunTrigger
import com.github.oxc.project.oxcintellijplugin.OxlintUnusedDisableDirectivesSeverity
import com.intellij.openapi.components.BaseState
import com.intellij.util.xml.Attribute

enum class ConfigurationMode {
    DISABLED,
    AUTOMATIC,
    MANUAL
}

class OxcSettingsState : BaseState() {

    @get:Attribute("binaryPath")
    var binaryPath by string()

    @get:Attribute("runTrigger")
    var runTrigger by enum(OxlintRunTrigger.ON_TYPE)

    @get:Attribute("configurationMode")
    var configurationMode by enum(ConfigurationMode.AUTOMATIC)

    @get:Attribute("configPath")
    var configPath by string()

    @get:Attribute("configPath")
    var fixAllOnSave by property(false)

    @get:Attribute("supportedExtensions")
    var supportedExtensions by list<String>()

    @get:Attribute("unusedDisableDirectives")
    var unusedDisableDirectives by enum(OxlintUnusedDisableDirectivesSeverity.ALLOW)

    companion object {
        val DEFAULT_EXTENSION_LIST = listOf(
            ".astro",
            ".js", ".jsx", ".cjs", ".mjs",
            ".svelte",
            ".ts", ".tsx", ".cts", ".mts",
            ".vue"
        )
    }
}
