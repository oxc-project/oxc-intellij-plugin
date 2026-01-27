package com.github.oxc.project.oxcintellijplugin.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.intellij.openapi.components.BaseState
import com.intellij.util.xml.Attribute

class OxfmtSettingsState : BaseState() {

    @get:Attribute("binaryPath")
    var binaryPath by string()

    @get:Attribute("configurationMode")
    var configurationMode by enum(ConfigurationMode.AUTOMATIC)

    @get:Attribute("configPath")
    var configPath by string()

    @get:Attribute("fixAllOnSave")
    var fixAllOnSave by property(false)

    @get:Attribute("supportedExtensions")
    var supportedExtensions by list<String>()

    companion object {

        val DEFAULT_EXTENSION_LIST = listOf(
            ".js", ".jsx", ".cjs", ".mjs",
            ".ts", ".tsx", ".cts", ".mts",
            ".json", ".json5",
            ".html", ".css", ".scss", ".less",
            ".gql", ".graphql",
            ".yml", ".yaml"
        )
    }
}
