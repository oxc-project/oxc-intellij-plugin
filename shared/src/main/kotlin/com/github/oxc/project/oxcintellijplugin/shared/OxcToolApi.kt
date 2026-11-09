@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable

/** Lets the frontend control the language servers, which run on the backend. */
@Rpc
interface OxcToolApi : RemoteApi<Unit> {

  /** Stops the tool's servers for the project, and starts the ones that its open files still need. */
  suspend fun restart(projectId: ProjectId, tool: OxcTool)

  /**
   * Starts the server that [fixAll] needs for the file, and returns why Fix All can't go on, or null once
   * it runs. Call it without a modal dialog showing, because the platform's start waits for one to close.
   */
  suspend fun startServerForFixAll(projectId: ProjectId, tool: OxcTool, file: VirtualFileId): OxcFixAllResult?

  /** Applies the tool's changes to the file, which doesn't have to be open in an editor. */
  suspend fun fixAll(projectId: ProjectId, tool: OxcTool, file: VirtualFileId): OxcFixAllResult

  /**
   * Asks the user to trust the project, which running anything from it needs, with the platform's dialog.
   * Returns whether it's trusted afterwards. The backend holds the project's trust.
   */
  suspend fun trustProject(projectId: ProjectId): Boolean

  companion object {
    suspend fun getInstance(): OxcToolApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxcToolApi>())
  }
}

/** The frontend decides how to tell the user about each of these. */
@Serializable
sealed interface OxcFixAllResult {

  @Serializable
  data object Applied : OxcFixAllResult

  @Serializable
  data object NothingToApply : OxcFixAllResult

  @Serializable
  data object UnsupportedFile : OxcFixAllResult

  /** Oxlint's fix kind is `None`. The server wasn't asked for anything. */
  @Serializable
  data object FixesTurnedOff : OxcFixAllResult

  /** The project isn't trusted, so nothing from it was run. */
  @Serializable
  data object Untrusted : OxcFixAllResult

  /** There's no package or manual path to run for the file. */
  @Serializable
  data object ToolNotFound : OxcFixAllResult

  @Serializable
  data object TimedOut : OxcFixAllResult

  @Serializable
  data class Failed(val message: String) : OxcFixAllResult
}
