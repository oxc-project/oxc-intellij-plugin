@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxfmt

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable

/** Lets the frontend ask for the project's code style to be changed, which only the backend can do. */
@Rpc
interface OxfmtCodeStyleApi : RemoteApi<Unit> {

  /** Imports the nearest config above [file] into the project's code style. */
  suspend fun importCodeStyle(projectId: ProjectId, file: VirtualFileId): OxfmtImportResult

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

  /** Node couldn't read the JavaScript or TypeScript config, or the project isn't trusted. */
  CONFIG_NOT_LOADED,

  /** The backend can't find the project or the file, such as one deleted since, or a project closing. */
  FILE_UNAVAILABLE,
}

/**
 * Why the backend didn't read a config. [configName] is for the title, and [configUrl] is the backend's
 * URL for it. It isn't a `VirtualFileId`, because one made on the backend reaches a thin client empty.
 */
@Serializable
sealed interface OxfmtCodeStyleNotice {
  val configUrl: String
  val configName: String

  /** Reading it would run the project's code, which only happens in a trusted project. */
  @Serializable
  data class Untrusted(override val configUrl: String, override val configName: String) : OxfmtCodeStyleNotice

  /** [message] is what went wrong, and [details] is everything known about it, such as the stack. */
  @Serializable
  data class Failed(
    override val configUrl: String,
    override val configName: String,
    val reason: OxfmtJsConfigFailure,
    val message: String,
    val details: String,
  ) : OxfmtCodeStyleNotice
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
