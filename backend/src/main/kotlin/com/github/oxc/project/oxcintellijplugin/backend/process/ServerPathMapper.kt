package com.github.oxc.project.oxcintellijplugin.backend.process

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Converts paths between the IDE and a language server, which may be on different machines. The LSP
 * client calls this on the UI thread, so implementations can't run a process or wait for anything.
 */
interface ServerPathMapper {

  /** Returns the path of a local file as the server sees it, or null if the server can't reach it. */
  fun toServerPath(localPath: String): String?

  /** Returns the local path of a file named by the server, or null if it has no local path. */
  fun toLocalPath(serverPath: String): String?
}

/**
 * Maps paths for a server in WSL. The [mountRoot] is passed in because reading
 * [WSLDistribution.getMntRoot] runs `wsl.exe` the first time, and giving the conversions a fixed value
 * means they can't do that. [workingDirectory] decides how the `\\wsl.localhost` or `\\wsl$` root of
 * a converted path is spelled, so that it finds the files that the project opened.
 */
class WslServerPathMapper(
  private val distribution: WSLDistribution,
  private val mountRoot: String,
  workingDirectory: String,
) : ServerPathMapper {

  private val workingDirectoryWslPath = WslPath.parseWindowsUncPath(workingDirectory)

  override fun toServerPath(localPath: String): String? =
    try {
      distribution.getWslPath(Path.of(localPath)) { mountRoot }
    }
    catch (_: InvalidPathException) {
      null
    }
    // Thrown when the path is in another distribution.
    catch (_: IllegalArgumentException) {
      null
    }

  override fun toLocalPath(serverPath: String): String? {
    // A path that starts with two slashes is a UNC path on the IDE's side, not one from the server.
    if (!serverPath.startsWith("/") || serverPath.startsWith("//")) return null

    val localPath = distribution.getWindowsPath(serverPath) { mountRoot }
    val wslPath = WslPath.parseWindowsUncPath(localPath) ?: return localPath
    val projectWslPath = workingDirectoryWslPath ?: return localPath
    return if (wslPath.distributionId.equals(projectWslPath.distributionId, ignoreCase = true)) {
      projectWslPath.wslRoot + localPath.removePrefix(wslPath.wslRoot)
    }
    else {
      localPath
    }
  }
}

/**
 * Maps paths for every other interpreter. A local interpreter's paths are returned as they are. Docker
 * and SSH use the folders given to [NodeTargetRun.path], which are known once the server has started.
 */
internal class TargetRunPathMapper(private val run: NodeTargetRun) : ServerPathMapper {

  override fun toServerPath(localPath: String): String? =
    try {
      run.convertLocalPathToTargetPath(localPath)
    }
    catch (_: IllegalArgumentException) {
      null
    }

  override fun toLocalPath(serverPath: String): String? =
    try {
      run.convertTargetPathToLocalPath(serverPath)
    }
    catch (_: IllegalArgumentException) {
      null
    }
}
