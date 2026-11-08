package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * The config that `oxfmt --lsp` uses for [file], following the same rules, so that the IDE's code style
 * matches what the server formats with. It can be an `oxfmt.config.ts` that the plugin can't read.
 * Returns null where the server would use its defaults. A selected folder counts as the first place
 * to look.
 *
 * The rules, from the server's source at 0.77.0:
 * - A manual config path is the only config, resolved from the server's root folder.
 * - Otherwise the root config is the first folder at or above the server's root that has one.
 * - With nested configs on, the nearest folder at or above the file that has one wins.
 * - A folder with more than one config is an error. The server falls back to the root config for a
 *   nested folder, and to its defaults for the root.
 */
fun findOxfmtConfig(project: Project, file: VirtualFile): VirtualFile? {
  val settings = OxfmtSettings.getInstance(project).state
  val root = oxfmtServerRoot(project, file)

  val manual = settings.configPath?.trim()?.takeIf { settings.configurationMode == ConfigurationMode.MANUAL && it.isNotEmpty() }
  if (manual != null) return resolveManualConfig(manual, root)

  val rootConfig = root?.let { configAtOrAbove(it) }
  if (settings.disableNestedConfig) return rootConfig

  var folder: VirtualFile? = if (file.isDirectory) file else file.parent
  while (folder != null) {
    if (rootConfig != null && folder == rootConfig.parent) return rootConfig
    when (val found = configIn(folder)) {
      is Found.One -> return found.file
      Found.Conflict -> return rootConfig
      null -> {}
    }
    folder = folder.parent
  }
  return rootConfig
}

/**
 * The folder the server runs from, which is where its root config search starts. Uses a running server's
 * root, then the package lookup, then the folder that the plugin used to search up to.
 */
fun oxfmtServerRoot(project: Project, file: VirtualFile): VirtualFile? =
  OxfmtDefinition.runningServerRoot(project, file)
  ?: OxfmtDefinition.createDescriptor(project, file)?.roots?.singleOrNull()
  ?: project.guessProjectDir()?.takeIf { VfsUtilCore.isAncestor(it, file, false) }
  ?: ProjectFileIndex.getInstance(project).getContentRootForFile(file)

/** A path that the server would read but this machine can't, such as one only inside WSL, isn't found. */
private fun resolveManualConfig(path: String, root: VirtualFile?): VirtualFile? {
  val normalized = FileUtil.toSystemIndependentName(path)
  val absolute = when {
    OSAgnosticPathUtil.isAbsolute(normalized) -> normalized
    root != null -> FileUtil.toCanonicalPath("${root.path}/$normalized")
    else -> return null
  }
  return LocalFileSystem.getInstance().findFileByPath(absolute)?.takeIf { !it.isDirectory }
}

/** The server's root search, which doesn't stop at the project's folder. */
private fun configAtOrAbove(start: VirtualFile): VirtualFile? {
  var folder: VirtualFile? = start
  while (folder != null) {
    when (val found = configIn(folder)) {
      is Found.One -> return found.file
      Found.Conflict -> return null
      null -> {}
    }
    folder = folder.parent
  }
  return null
}

private sealed interface Found {
  class One(val file: VirtualFile) : Found
  data object Conflict : Found
}

private fun configIn(folder: VirtualFile): Found? {
  val configs = OXFMT_CONFIG_NAMES.mapNotNull { folder.findChild(it)?.takeIf { file -> !file.isDirectory } }
  return when (configs.size) {
    0 -> null
    1 -> Found.One(configs.single())
    else -> Found.Conflict
  }
}
