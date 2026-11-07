@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxlint

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable

/** Lets the frontend control the Oxlint language servers, which run on the backend. */
@Rpc
interface OxlintServerApi : RemoteApi<Unit> {

  /** Stops the project's servers, and starts the ones that its open files still need. */
  suspend fun restart(projectId: ProjectId)

  /** Applies the preferred fixes to the file, which doesn't have to be open in an editor. */
  suspend fun fixAll(projectId: ProjectId, file: VirtualFileId): OxlintFixAllResult

  companion object {
    suspend fun getInstance(): OxlintServerApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxlintServerApi>())
  }
}

/** The frontend decides how to tell the user about each of these. */
@Serializable
sealed interface OxlintFixAllResult {

  @Serializable
  data object Applied : OxlintFixAllResult

  @Serializable
  data object NothingToApply : OxlintFixAllResult

  @Serializable
  data object UnsupportedFile : OxlintFixAllResult

  /** The fix kind is `None`. The server wasn't asked for anything. */
  @Serializable
  data object FixesTurnedOff : OxlintFixAllResult

  /** There's no `oxlint` package or manual path to run for the file. */
  @Serializable
  data object OxlintNotFound : OxlintFixAllResult

  @Serializable
  data object TimedOut : OxlintFixAllResult

  @Serializable
  data class Failed(val message: String) : OxlintFixAllResult
}
