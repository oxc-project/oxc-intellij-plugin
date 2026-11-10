@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxfmt

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Lets the frontend ask for the project's code style to be changed, which only the backend can do, and
 * tells it what to show when the backend can't read a config or has imported one by itself.
 */
@Rpc
interface OxfmtCodeStyleApi : RemoteApi<Unit> {

  /** Imports the nearest config above [file] into the project's code style. */
  suspend fun importCodeStyle(projectId: ProjectId, file: VirtualFileId): OxfmtImportResult

  /**
   * Each problem reading a JavaScript or TypeScript config that the frontend should show, and each
   * automatic import that changed the project's code style. The backend decides which to send, so each one
   * is shown. One that comes before the frontend asks waits for it.
   */
  suspend fun configNotices(projectId: ProjectId): Flow<OxfmtConfigNotice>

  /** Opens the config at [configUrl] in an editor. A thin client can't find a file it hasn't opened, so the backend opens it. */
  suspend fun openConfig(projectId: ProjectId, configUrl: String)

  /** Opens the Node.js settings page, which a thin client's settings dialog can't select, because it's the backend's. */
  suspend fun showNodeSettings(projectId: ProjectId)

  companion object {
    suspend fun getInstance(): OxfmtCodeStyleApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxfmtCodeStyleApi>())
  }
}

@Serializable
enum class OxfmtImportResult {
  IMPORTED,
  ALREADY_IMPORTED,
  CONFIG_NOT_FOUND,

  /** The config is JavaScript or TypeScript, and Oxfmt is a native binary that can't read it either. */
  CONFIG_NOT_READABLE,

  /** Node couldn't read the JavaScript or TypeScript config, or the project isn't trusted. The backend has said why. */
  CONFIG_NOT_LOADED,

  /** The backend can't find the project or the file, such as one deleted since, or a project closing. */
  FILE_UNAVAILABLE,
}

/**
 * Why the backend didn't read a config, or that it imported one. [configName] is for the title, and
 * [configUrl] is the backend's URL for it, for [OxfmtCodeStyleApi.openConfig]. It isn't a `VirtualFileId`,
 * because one made on the backend reaches a thin client empty.
 */
@Serializable
sealed interface OxfmtConfigNotice {
  val configUrl: String
  val configName: String

  /** Reading it would run the project's code, which only happens in a trusted project. */
  @Serializable
  data class Untrusted(override val configUrl: String, override val configName: String) : OxfmtConfigNotice

  /** A change to the config made the backend import it into the project's code style. */
  @Serializable
  data class Imported(override val configUrl: String, override val configName: String) : OxfmtConfigNotice

  /** [message] is what went wrong, and [details] is everything known about it, such as the stack. */
  @Serializable
  data class Failed(
    override val configUrl: String,
    override val configName: String,
    val reason: OxfmtJsConfigFailure,
    val message: String,
    val details: String,
  ) : OxfmtConfigNotice
}

@Serializable
enum class OxfmtJsConfigFailure {
  /** No Node interpreter, or one that isn't valid. */
  NO_NODE,

  /** The interpreter can't load TypeScript, which needs Node.js 22.18 or later. */
  OLD_NODE,

  /** The config threw, `vite-plus` couldn't be loaded, Node timed out, or its answer made no sense. */
  ERROR,
}
