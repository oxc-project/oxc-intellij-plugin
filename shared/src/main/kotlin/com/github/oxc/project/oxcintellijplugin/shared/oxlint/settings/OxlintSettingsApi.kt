@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.isSupportedExtension
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

val DEFAULT_OXLINT_EXTENSIONS: List<String> = listOf(
  ".astro",
  ".js", ".jsx", ".cjs", ".mjs",
  ".svelte",
  ".ts", ".tsx", ".cts", ".mts",
  ".vue",
)

/**
 * Exposes the Oxlint settings for edits over RPC, primarily for use by the settings page. A `null`
 * project indicates the default project is being used.
 */
@Rpc
interface OxlintSettingsApi : RemoteApi<Unit> {

  suspend fun load(projectId: ProjectId?): OxlintSettingsData

  /** Stores the settings, and restarts the servers if necessary. */
  suspend fun apply(projectId: ProjectId?, settings: OxlintSettingsData)

  /**
   * Emits the current settings and each change. This allows accessing the current settings in a way
   * that doesn't require waiting for a full [load].
   */
  suspend fun updates(projectId: ProjectId): Flow<OxlintSettingsData>

  companion object {

    suspend fun getInstance(): OxlintSettingsApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxlintSettingsApi>())
  }
}

/** Settings that can be edited through RPC. */
@Serializable
data class OxlintSettingsData(
  val configurationMode: ConfigurationMode = ConfigurationMode.AUTOMATIC,
  val binaryPath: String = "",
  val configPath: String = "",
  val unusedDisableDirectives: OxlintUnusedDisableDirectives = OxlintUnusedDisableDirectives.ALLOW,
  /** Empty means [DEFAULT_OXLINT_EXTENSIONS]. Any other list replaces them. */
  val supportedExtensions: List<String> = emptyList(),
  val typeAware: Boolean = false,
  val disableNestedConfig: Boolean = false,
  val fixKind: OxlintFixKind = OxlintFixKind.SAFE_FIX,
)

/** An empty list of [configured] extensions means [DEFAULT_OXLINT_EXTENSIONS]. */
fun isOxlintSupportedExtension(configured: List<String>, extension: String?): Boolean =
  isSupportedExtension(configured, DEFAULT_OXLINT_EXTENSIONS, extension)
