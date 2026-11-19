@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

/** Lets the frontend control the language servers, which run on the backend. */
@Rpc
interface OxcToolApi : RemoteApi<Unit> {

  /** Stops the tool's servers for the project, and starts the ones that its open files still need. */
  suspend fun restart(projectId: ProjectId, tool: OxcTool)

  /** The project's folder as the backend sees it, or null if it cannot be found. */
  suspend fun projectPath(projectId: ProjectId): String?

  companion object {

    suspend fun getInstance(): OxcToolApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxcToolApi>())
  }
}
