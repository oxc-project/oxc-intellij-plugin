@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.files

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable

/** The folder that lists the backend's file system roots, such as the drives on Windows. */
const val BACKEND_ROOTS_PATH: String = ""

/**
 * Lets the frontend browse the backend's files. A frontend's file choosers show its own machine, and
 * settings such as the config path name files on the backend.
 */
@Rpc
interface BackendFilesApi : RemoteApi<Unit> {

  /**
   * Lists a folder. A blank path means the project's folder, a relative path is relative to it, and a
   * file means the folder containing it. A path that can't be read returns its error and the closest folder above it. A null project
   * means the default project, which has no folder or [ProjectId].
   */
  suspend fun listFolder(projectId: ProjectId?, path: String?): BackendFolder

  companion object {
    suspend fun getInstance(): BackendFilesApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<BackendFilesApi>())
  }
}

@Serializable
data class BackendFolder(
  val path: String,
  /** Null if this folder has nothing above it. */
  val parent: String?,
  /** Folders first, then files, each sorted by name. */
  val entries: List<BackendFileEntry>,
  val error: String? = null,
)

@Serializable
data class BackendFileEntry(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
)
