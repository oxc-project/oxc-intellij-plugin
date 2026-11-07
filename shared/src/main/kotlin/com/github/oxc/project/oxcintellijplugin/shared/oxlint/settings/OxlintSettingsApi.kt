@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
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
 * Lets the settings page edit the settings, which only the backend holds and writes to
 * `.idea/OxcSettings.xml`. A null project means the default project, which has no [ProjectId] to look
 * up. The platform's settings sync isn't used because a frontend's change reached the backend's memory
 * but never its file, even when the backend exited normally.
 */
@Rpc
interface OxlintSettingsApi : RemoteApi<Unit> {

  suspend fun load(projectId: ProjectId?): OxlintSettingsData

  /** Stores the settings, and restarts the servers if anything changed. */
  suspend fun apply(projectId: ProjectId?, settings: OxlintSettingsData)

  /** The current settings, then each change, for frontend code that can't wait for a [load]. */
  suspend fun updates(projectId: ProjectId): Flow<OxlintSettingsData>

  companion object {
    suspend fun getInstance(): OxlintSettingsApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxlintSettingsApi>())
  }
}

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
  extension != null && ".$extension" in configured.ifEmpty { DEFAULT_OXLINT_EXTENSIONS }
