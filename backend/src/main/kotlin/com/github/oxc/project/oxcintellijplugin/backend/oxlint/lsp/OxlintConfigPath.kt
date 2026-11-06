package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil

/**
 * The manual config path, ready to send to a server.
 *
 * The server asks to be told when this file changes, and the platform matches a plain glob against
 * the local path. A server in WSL would register `/mnt/c/...`, which never matches `C:/...`, so a file
 * in the workspace is sent as a relative path, which the platform resolves against the workspace.
 * A file outside it can't work that way, so [fileOutsideWorkspace] tells the plugin to watch it.
 */
data class OxlintConfigPath(val serverValue: String, val fileOutsideWorkspace: String?) {

  companion object {
    fun of(
      configured: String?,
      workspace: String,
      toServerPath: (String) -> String?,
      toLocalPath: (String) -> String?,
    ): OxlintConfigPath? {
      val path = configured?.trim()?.takeIf { it.isNotEmpty() }?.let(FileUtil::toSystemIndependentName)
                 ?: return null
      val localPath = if (OSAgnosticPathUtil.isAbsolute(path)) {
        toLocalPath(path)?.let(FileUtil::toSystemIndependentName) ?: path
      }
      else {
        FileUtil.toCanonicalPath("$workspace/$path")
      }

      if (FileUtil.isAncestor(workspace, localPath, false)) {
        return OxlintConfigPath(FileUtil.getRelativePath(workspace, localPath, '/') ?: path, null)
      }
      val serverValue = if (OSAgnosticPathUtil.isAbsolute(path)) toServerPath(localPath) ?: path else path
      return OxlintConfigPath(serverValue, localPath)
    }
  }
}
