package com.github.oxc.project.oxcintellijplugin.backend.files

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.shared.files.BACKEND_ROOTS_PATH
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFileEntry
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFolder
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries

/** Lists folders for [com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi]. */
class BackendFolderLister(private val defaultFolder: String?) {

  fun list(requestedPath: String?): BackendFolder {
    if (requestedPath == BACKEND_ROOTS_PATH) return listRoots()

    val requested = requestedPath?.takeIf { it.isNotBlank() } ?: defaultFolder ?: return listRoots()
    val path = try {
      Path.of(requested).toAbsolutePath().normalize()
    }
    catch (_: InvalidPathException) {
      return listRoots().copy(error = OxcBackendBundle.message("files.path.invalid", requested))
    }

    val folder = closestFolder(path) ?: return listRoots().copy(
      error = OxcBackendBundle.message("files.path.not.found", requested))
    val missing = !Files.exists(path)
    return try {
      listFolder(folder).copy(
        error = if (missing) OxcBackendBundle.message("files.path.not.found", requested) else null)
    }
    catch (exception: IOException) {
      BackendFolder(
        path = folder.toString(),
        parent = parentOf(folder),
        entries = emptyList(),
        error = OxcBackendBundle.message("files.folder.not.readable", folder, exception.message.orEmpty()),
      )
    }
  }

  private fun closestFolder(path: Path): Path? =
    generateSequence(path) { it.parent }.firstOrNull { it.isDirectory() }

  private fun listFolder(folder: Path): BackendFolder {
    val entries = folder.useDirectoryEntries { paths ->
      paths.map { BackendFileEntry(it.name, it.toString(), it.isDirectory()) }.toList()
    }
    return BackendFolder(
      path = folder.toString(),
      parent = parentOf(folder),
      entries = entries.sortedWith(ENTRY_ORDER),
    )
  }

  private fun listRoots(): BackendFolder = BackendFolder(
    path = BACKEND_ROOTS_PATH,
    parent = null,
    entries = FileSystems.getDefault().rootDirectories
      .map { BackendFileEntry(it.toString(), it.toString(), true) }
      .sortedWith(ENTRY_ORDER),
  )

  /** A single root is the top of the file system, while several need a list to choose from. */
  private fun parentOf(folder: Path): String? =
    folder.parent?.toString()
    ?: BACKEND_ROOTS_PATH.takeIf { FileSystems.getDefault().rootDirectories.count() > 1 }

  private companion object {
    val ENTRY_ORDER: Comparator<BackendFileEntry> =
      compareBy<BackendFileEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
  }
}
