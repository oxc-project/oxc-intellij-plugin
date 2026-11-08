package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
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
 * Restarts a tool's servers when its manual config outside the workspace changes. Nothing else would
 * notice, because the IDE may not have loaded the file, and a server in WSL registers a path that the
 * platform can't match. Files in the workspace don't need this, because the server reloads those itself.
 */
@Service(Service.Level.PROJECT)
class OutsideConfigWatcher : Disposable {

  private val watches = mutableMapOf<OxcTool, Watch>()

  /** Reads from the disk, so this has to be called on a background thread. */
  @Synchronized
  fun watch(tool: OxcTool, file: String?) {
    if (file == watches[tool]?.file) return

    val fileSystem = LocalFileSystem.getInstance()
    watches.remove(tool)?.request?.let(fileSystem::removeWatchedRoot)
    if (file != null) {
      // The file has to be loaded for changes to it to be reported.
      fileSystem.refreshAndFindFileByPath(file)
      watches[tool] = Watch(file, fileSystem.addRootToWatch(file, false))
    }
  }

  @Synchronized
  fun isWatched(tool: OxcTool, path: String): Boolean =
    watches[tool]?.let { FileUtil.pathsEqual(it.file, path) } == true

  override fun dispose() {
    OxcTool.entries.forEach { watch(it, null) }
  }

  private class Watch(val file: String, val request: LocalFileSystem.WatchRequest?)

  /** Listens on the project's message bus, so it only exists while the project is open. */
  class Listener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
      val watcher = project.service<OutsideConfigWatcher>()
      val changed = ToolDefinition.all.filter { definition ->
        events.any { watcher.isWatched(definition.tool, it.path) }
      }
      if (changed.isEmpty()) return

      ApplicationManager.getApplication().invokeLater({
        changed.forEach { LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(it.providerClass) }
      }, project.disposed)
    }
  }
}
