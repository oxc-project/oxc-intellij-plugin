@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.oxlint

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

/** Lets the frontend control the Oxlint language servers, which run on the backend. */
@Rpc
interface OxlintServerApi : RemoteApi<Unit> {

  /** Stops the project's servers, and starts the ones that its open files still need. */
  suspend fun restart(projectId: ProjectId)

  companion object {
    suspend fun getInstance(): OxlintServerApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<OxlintServerApi>())
  }
}
