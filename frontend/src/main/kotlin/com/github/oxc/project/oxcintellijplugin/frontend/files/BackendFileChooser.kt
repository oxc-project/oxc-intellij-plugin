@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.files

import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks for a file on the machine that runs the language servers. A thin client's chooser is limited
 * to the backend's files, and the path it returns is the backend's.
 */
fun chooseBackendFile(project: Project, title: String, currentPath: String): String? {
  val descriptor = FileChooserDescriptorFactory.singleFile().withTitle(title)
  if (!IdeProductMode.isFrontend) {
    val current = currentPath.takeIf { it.isNotBlank() }
      ?.let(LocalFileSystem.getInstance()::findFileByPath)
    return FileChooser.chooseFile(descriptor, project, current)?.path
  }

  val backend = runWithModalProgressBlocking(project, title) {
    val projectPath = backendProjectPath(project)
    withContext(Dispatchers.IO) { BackendLocation.find(project, projectPath, currentPath) }
  }
  val chosen = FileChooser.chooseFile(backend.restrict(descriptor), project, backend.current)
  return chosen?.toNioPath()?.asEelPath()?.toString()
}

/** The frontend's own project folder is on the client's machine. */
private suspend fun backendProjectPath(project: Project): String? {
  if (project.isDefault) return null
  val projectId = project.projectId()
  return try {
    withContext(Dispatchers.Default) { durable { OxcToolApi.getInstance().projectPath(projectId) } }
  } catch (exception: CancellationException) {
    throw exception
  } catch (exception: Exception) {
    logger<BackendLocation>().warn(exception)
    null
  }
}

private class BackendLocation(val root: VirtualFile?, val current: VirtualFile?) {

  /** Without the backend's root, only the project's environment is shown. */
  fun restrict(descriptor: FileChooserDescriptor): FileChooserDescriptor =
    root?.let(descriptor::withRoots) ?: descriptor.withEnvironmentRestricted(true)

  companion object {

    fun find(project: Project, projectPath: String?, currentPath: String): BackendLocation {
      val backend = project.getEelDescriptor()
      val root = (backend as? EelPathBoundDescriptor)?.rootPath?.let(::findFile)
      val current = clientPath(backend, projectPath, currentPath)?.let { path ->
        generateSequence(path) { it.parent }.firstNotNullOfOrNull(::findFile)
      }
      return BackendLocation(root, current)
    }

    /** A blank path means the project's folder, and a relative path is relative to it. */
    private fun clientPath(backend: EelDescriptor, projectPath: String?, path: String): Path? {
      val project = projectPath?.let { parse(it, backend) }
      val eelPath = when {
        path.isBlank() -> project
        else -> parse(path, backend) ?: project?.let { resolve(it, path) }
      }
      return eelPath?.let { runCatching { it.asNioPath() }.getOrNull() }
    }

    private fun parse(path: String, backend: EelDescriptor): EelPath? =
      try {
        EelPath.parse(path, backend)
      } catch (_: EelPathException) {
        null
      }

    private fun resolve(base: EelPath, relative: String): EelPath? =
      try {
        base.resolve(relative)
      } catch (_: EelPathException) {
        null
      }

    private fun findFile(path: Path): VirtualFile? =
      VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path)
  }
}
