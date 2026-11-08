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

  /** The config that the server uses is a TypeScript file, which the plugin can't read. */
  CONFIG_NOT_READABLE,
}
