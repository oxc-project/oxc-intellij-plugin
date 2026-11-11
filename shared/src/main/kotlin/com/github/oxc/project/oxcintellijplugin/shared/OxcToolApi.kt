@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

/** Lets the frontend ask the backend, which runs the tools, about the project. */
@Rpc
interface OxcToolApi : RemoteApi<Unit> {

  /** The project's folder as the backend sees it, or null if it cannot be found. */
  suspend fun projectPath(projectId: ProjectId): String?

  companion object {

    suspend fun getInstance(): OxcToolApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxcToolApi>())
  }
}
