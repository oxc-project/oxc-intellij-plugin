package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxlintRunTrigger
import com.github.oxc.project.oxcintellijplugin.OxlintUnusedDisableDirectivesSeverity
import com.intellij.openapi.components.BaseState
import com.intellij.util.xml.Attribute
import com.intellij.util.xmlb.annotations.XMap

enum class ConfigurationMode {
    DISABLED,
    AUTOMATIC,
    MANUAL
}

class OxcSettingsState : BaseState() {

    @get:Attribute("binaryPath")
    var binaryPath by string()

    @get:Attribute("binaryParameters")
    var binaryParameters by list<String>()

    @get:Attribute("runTrigger")
    var runTrigger by enum(OxlintRunTrigger.ON_TYPE)

    @get:Attribute("configurationMode")
    var configurationMode by enum(ConfigurationMode.AUTOMATIC)

    @get:Attribute("configPath")
    var configPath by string()

    @get:Attribute("fixAllOnSave")
    var fixAllOnSave by property(false)

    @get:Attribute("flags")
    @get:XMap
    var flags by map<String, String>()

    @get:Attribute("supportedExtensions")
    var supportedExtensions by list<String>()

    @get:Attribute("typeAware")
    var typeAware by property(false)

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
