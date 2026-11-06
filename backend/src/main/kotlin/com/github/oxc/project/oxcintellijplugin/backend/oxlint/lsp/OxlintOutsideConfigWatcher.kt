package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.lsp.api.LspClientManager

/**
 * Restarts the servers when a manual config outside the workspace changes. Nothing else would notice,
 * because the IDE may not have loaded the file, and a server in WSL registers a path that the platform
 * can't match. Files in the workspace don't need this, because the server reloads those itself.
 */
@Service(Service.Level.PROJECT)
class OxlintOutsideConfigWatcher(private val project: Project) : Disposable {

  private var watchedFile: String? = null
  private var watchRequest: LocalFileSystem.WatchRequest? = null

  /** Reads from the disk, so this has to be called on a background thread. */
  @Synchronized
  fun watch(file: String?) {
    if (file == watchedFile) return

    val fileSystem = LocalFileSystem.getInstance()
    watchRequest?.let(fileSystem::removeWatchedRoot)
    watchRequest = file?.let {
      // The file has to be loaded for changes to it to be reported.
      fileSystem.refreshAndFindFileByPath(it)
      fileSystem.addRootToWatch(it, false)
    }
    watchedFile = file
  }

  fun isWatched(path: String): Boolean =
    watchedFile?.let { FileUtil.pathsEqual(it, path) } == true

  override fun dispose() {
    watch(null)
  }

  /** Listens on the project's message bus, so it only exists while the project is open. */
  class Listener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
      val watcher = project.service<OxlintOutsideConfigWatcher>()
      if (events.none { watcher.isWatched(it.path) }) return

      ApplicationManager.getApplication().invokeLater({
        LspClientManager.getInstance(project)
          .stopAndRestartClientsIfNeeded(OxlintLspIntegrationProvider::class.java)
      }, project.disposed)
    }
  }
}
