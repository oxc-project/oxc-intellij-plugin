@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.isSupportedExtension
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

val DEFAULT_OXFMT_EXTENSIONS: List<String> = listOf(
  // JS/TS
  ".js", ".jsx", ".cjs", ".mjs", ".ts", ".tsx", ".cts", ".mts",
  "._js", ".bones", ".es", ".es6", ".gs", ".jake", ".javascript", ".jsb", ".jscad", ".jsfl", ".jslib",
  ".jsm", ".jspre", ".jss", ".njs", ".pac", ".sjs", ".ssjs", ".xsjs", ".xsjslib",
  // JSON
  ".json", ".4DForm", ".4DProject", ".avsc", ".geojson", ".gltf", ".har", ".ice", ".JSON-tmLanguage",
  ".mcmeta", ".sarif", ".tact", ".tfstate", ".topojson", ".webapp", ".webmanifest", ".yy", ".yyp",
  // JSONC
  ".jsonc", ".json5", ".code-snippets", ".code-workspace", ".sublime-build", ".sublime-color-scheme",
  ".sublime-commands", ".sublime-completions", ".sublime-keymap", ".sublime-macro", ".sublime-menu",
  ".sublime-mousemap", ".sublime-project", ".sublime-settings", ".sublime-theme", ".sublime-workspace",
  ".sublime_metrics", ".sublime_session",
  // HTML
  ".html", ".hta", ".htm", ".inc", ".xht", ".xhtml",
  // Frameworks
  ".handlebars", ".hbs", ".mjml", ".vue", ".svelte", ".astro",
  // CSS
  ".css", ".pcss", ".postcss", ".less", ".scss", ".wxss",
  // GraphQL
  ".gql", ".graphql", ".graphqls",
  // Markdown
  ".md", ".livemd", ".markdown", ".mdown", ".mdwn", ".mdx", ".mkd", ".mkdn", ".mkdown", ".ronn", ".scd",
  ".workbook",
  // YAML
  ".yaml", ".yaml-tmlanguage", ".yml", ".mir", ".reek", ".rviz", ".sublime-syntax", ".syntax",
  // TOML
  ".toml",
)

/** See `OxlintSettingsApi`, which this follows. The file here is `.idea/OxfmtSettings.xml`. */
@Rpc
interface OxfmtSettingsApi : RemoteApi<Unit> {

  suspend fun load(projectId: ProjectId?): OxfmtSettingsData

  /** Stores the settings, and restarts the servers if a change affects them. */
  suspend fun apply(projectId: ProjectId?, settings: OxfmtSettingsData)

  /** The current settings, then each change, for frontend code that can't wait for a [load]. */
  suspend fun updates(projectId: ProjectId): Flow<OxfmtSettingsData>

  companion object {
    suspend fun getInstance(): OxfmtSettingsApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxfmtSettingsApi>())
  }
}

/** What the settings page edits. Fix on save isn't here, because the Actions on Save row is what edits it. */
@Serializable
data class OxfmtSettingsData(
  val configurationMode: ConfigurationMode = ConfigurationMode.AUTOMATIC,
  val binaryPath: String = "",
  val configPath: String = "",
  /** Empty means [DEFAULT_OXFMT_EXTENSIONS]. Any other list replaces them. */
  val supportedExtensions: List<String> = emptyList(),
  val disableNestedConfig: Boolean = false,
  val preferOxfmtCodeStyleSettings: Boolean = false,
)

/** An empty list of [configured] extensions means [DEFAULT_OXFMT_EXTENSIONS]. */
fun isOxfmtSupportedExtension(configured: List<String>, extension: String?): Boolean =
  isSupportedExtension(configured, DEFAULT_OXFMT_EXTENSIONS, extension)
