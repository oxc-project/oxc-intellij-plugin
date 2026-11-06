package com.github.oxc.project.oxcintellijplugin.backend.lsp

import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.URLUtil

/**
 * Formats a path as a file URI the same way `LspClientDescriptor` does.
 */
fun pathToFileUri(path: String): String {
  if (OSAgnosticPathUtil.startsWithWindowsDrive(path)) {
    val encodedColon = "%3A"
    return "file:///${path[0].lowercaseChar()}$encodedColon${URLUtil.encodePath(path.substring(2))}"
  }

  val encodedPath = URLUtil.encodePath(path)
  if (path.startsWith("//")) return "file:$encodedPath"

  val url = VirtualFileManager.constructUrl("file", encodedPath)
  return VfsUtil.toUri(url)?.toString() ?: url
}
