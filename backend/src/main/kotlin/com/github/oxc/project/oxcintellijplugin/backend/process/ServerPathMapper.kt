package com.github.oxc.project.oxcintellijplugin.backend.process

import com.intellij.javascript.nodejs.execution.NodeTargetRun

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
 * Maps paths through the Node interpreter's run. A local interpreter's paths are returned as they are.
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
