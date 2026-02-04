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
            // JS/TS
            ".js", ".jsx", ".cjs", ".mjs",
            ".ts", ".tsx", ".cts", ".mts",
            "._js", ".bones", ".es", ".es6", ".gs", ".jake", ".javascript",
            ".jsb", ".jscad", ".jsfl", ".jslib", ".jsm", ".jspre", ".jss",
            ".njs", ".pac", ".sjs", ".ssjs", ".xsjs", ".xsjslib",
            ".start.frag", ".end.frag"
            // JSON
            ".json", ".4DForm", ".4DProject", ".avsc", ".geojson", ".gltf",
            ".har", ".ice", ".JSON-tmLanguage", ".json.example", ".mcmeta",
            ".sarif", ".tact", ".tfstate", ".tfstate.backup", ".topojson",
            ".webapp", ".webmanifest", ".yy", ".yyp",
            // JSONC
            ".jsonc", ".json5" ".code-snippets", ".code-workspace", ".sublime-build",
            ".sublime-color-scheme", ".sublime-commands", ".sublime-completions",
            ".sublime-keymap", ".sublime-macro", ".sublime-menu", ".sublime-mousemap",
            ".sublime-project", ".sublime-settings", ".sublime-theme",
            ".sublime-workspace", ".sublime_metrics", ".sublime_session",
            // HTML
            ".html", ".hta", ".htm", ".inc", ".xht", ".xhtml",
            // Frameworks
            ".handlebars", ".hbs", ".mjml", ".vue",
            // CSS
            ".css", ".pcss", ".postcss", ".less", ".scss", ".wxss",
            // GraphQL
            ".gql", ".graphql", ".graphqls",
            // Markdown
            ".md", ".livemd", ".markdown", ".mdown", ".mdwn", ".mdx", ".mkd",
            ".mkdn", ".mkdown", ".ronn", ".scd", ".workbook",
            // YAML
            ".yaml", ".yaml-tmlanguage", ".yml", ".mir", ".reek", ".rviz",
            ".sublime-syntax", ".syntax",
            // TOML
            ".toml",
            ".toml.example",
        )
    }
}