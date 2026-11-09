package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.process.isNodeScript
import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
import com.github.oxc.project.oxcintellijplugin.backend.tool.ServerLaunch
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager

/** How the server reads a config, which is how the plugin has to read it too. */
enum class OxfmtConfigFormat {
  JSON,

  /** The default export of a JavaScript or TypeScript file, which only Node can read. */
  JAVASCRIPT,

  /** The `fmt` field of the config that `vite-plus`'s `resolveConfig` gives, under `vp`. */
  VITE_PLUS,

  /** A JavaScript or TypeScript config for a native binary, which can't read one either. */
  UNREADABLE,
}

/**
 * A config that the server uses for a file. Under `vp`, the server skips a `vite.config.*` that has no
 * `fmt` field and keeps looking above it, so [fallbacks] are the configs it would try next, in order.
 * A manual config is [explicit], and has to have a `fmt` field under `vp`.
 */
data class OxfmtConfigLocation(
  val file: VirtualFile,
  val format: OxfmtConfigFormat,
  val launch: ServerLaunch?,
  val fallbacks: List<VirtualFile> = emptyList(),
  val explicit: Boolean = false,
)

/**
 * The config that `oxfmt --lsp` uses for [file], so that the IDE's code style matches what the server
 * formats with. Returns null where the server would use its defaults. A selected folder counts as the
 * first place to look.
 */
fun findOxfmtConfig(project: Project, file: VirtualFile): VirtualFile? = locateOxfmtConfig(project, file)?.file

/**
 * Follows the server's rules, from its source at 0.77.0:
 * - A manual config path is the only config, resolved from the server's root folder.
 * - Under `vp`, the server only looks for `vite.config.*`, from its root folder upward, and has no nested
 *   configs. The first name in Vite's order wins in a folder.
 * - Otherwise the root config is the first folder at or above the server's root that has one.
 * - With nested configs on, the nearest folder at or above the file that has one wins.
 * - A folder with more than one config is an error. The server falls back to the root config for a
 *   nested folder, and to its defaults for the root.
 */
fun locateOxfmtConfig(project: Project, file: VirtualFile): OxfmtConfigLocation? {
  val settings = OxfmtSettings.getInstance(project).state
  val launch = oxfmtLaunch(project, file)
  val root = launch?.root ?: fallbackRoot(project, file)
  val native = launch != null && settings.configurationMode == ConfigurationMode.MANUAL &&
               !isNodeScript(launch.executable, launch.root.path)

  fun locate(config: VirtualFile, explicit: Boolean = false): OxfmtConfigLocation {
    val format = when {
      isReadableOxfmtConfig(config) -> OxfmtConfigFormat.JSON
      native -> OxfmtConfigFormat.UNREADABLE
      launch?.vitePlusFolder != null -> OxfmtConfigFormat.VITE_PLUS
      else -> OxfmtConfigFormat.JAVASCRIPT
    }
    return OxfmtConfigLocation(config, format, launch, explicit = explicit)
  }

  val manual = settings.configPath?.trim()?.takeIf { settings.configurationMode == ConfigurationMode.MANUAL && it.isNotEmpty() }
  if (manual != null) return resolveManualConfig(resolveSettingPath(project, manual), root)?.let { locate(it, explicit = true) }

  if (launch?.vitePlusFolder != null) {
    val configs = root?.let(::viteConfigsAtOrAbove).orEmpty()
    return configs.firstOrNull()?.let { OxfmtConfigLocation(it, OxfmtConfigFormat.VITE_PLUS, launch, configs.drop(1)) }
  }

  val rootConfig = root?.let { configAtOrAbove(it) }
  if (settings.disableNestedConfig) return rootConfig?.let(::locate)

  var folder: VirtualFile? = if (file.isDirectory) file else file.parent
  while (folder != null) {
    if (rootConfig != null && folder == rootConfig.parent) return locate(rootConfig)
    when (val found = configIn(folder)) {
      is Found.One -> return locate(found.file)
      Found.Conflict -> return rootConfig?.let(::locate)
      null -> {}
    }
    folder = folder.parent
  }
  return rootConfig?.let(::locate)
}

/** What runs for [file]: a running server's launch, then the package lookup. */
fun oxfmtLaunch(project: Project, file: VirtualFile): ServerLaunch? =
  LspClientManager.getInstance(project).getClients(OxfmtDefinition.providerClass)
    .firstNotNullOfOrNull { client ->
      (client.descriptor as? ToolLspClientDescriptor)?.takeIf { it.isSupportedFile(file) }?.launch
    }
  ?: OxfmtDefinition.createDescriptor(project, file)?.launch

/** Where the root config search starts when nothing would run: the folder the plugin used to search up to. */
private fun fallbackRoot(project: Project, file: VirtualFile): VirtualFile? =
  project.guessProjectDir()?.takeIf { VfsUtilCore.isAncestor(it, file, false) }
  ?: ProjectFileIndex.getInstance(project).getContentRootForFile(file)

/**
 * A path that the server would read but this machine can't isn't found, such as a Linux path for a server
 * in WSL when the project is on the Windows side. In a project in WSL or a container, [path] has already
 * been resolved, so its Linux paths are found.
 */
private fun resolveManualConfig(path: String, root: VirtualFile?): VirtualFile? {
  val absolute = manualConfigPath(path, root?.path) ?: return null
  return LocalFileSystem.getInstance().findFileByPath(absolute)?.takeIf { !it.isDirectory }
}

/**
 * The full path of a manual config [path] that has already been through `resolveSettingPath`, as the
 * lookup reads it from the server's [root]. Null for a relative path without a root.
 */
internal fun manualConfigPath(path: String, root: String?): String? {
  val normalized = FileUtil.toSystemIndependentName(path)
  return when {
    OSAgnosticPathUtil.isAbsolute(normalized) -> normalized
    root != null -> FileUtil.toCanonicalPath("$root/$normalized")
    else -> null
  }
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

/** One `vite.config.*` per folder, from [start] upward, in the order that the server tries them. */
private fun viteConfigsAtOrAbove(start: VirtualFile): List<VirtualFile> =
  generateSequence(start) { it.parent }
    .mapNotNull { folder -> VITE_CONFIG_NAMES.firstNotNullOfOrNull { folder.findChild(it)?.takeIf { file -> !file.isDirectory } } }
    .toList()

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
