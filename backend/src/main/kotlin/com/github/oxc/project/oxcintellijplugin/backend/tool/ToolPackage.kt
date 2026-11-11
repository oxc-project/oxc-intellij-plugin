package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import java.nio.file.Paths

/** What to run for a file, and where its server should run from. */
data class ServerLaunch(
  val executable: String,
  val parameters: List<String>,
  val root: VirtualFile,
)

/** Finds what to run for a file: the tool's package, or the manual path. */
class ToolPackage(private val project: Project, private val definition: ToolDefinition) {

  /** Returns null if there's nothing to run. */
  fun resolve(file: VirtualFile): ServerLaunch? {
    val manualBinary = manualBinary()
    val nodePackage = find(file)
    val root = serverRoot(file, nodePackage, manualBinary != null)
    val executable = manualBinary?.let { binary -> root?.let { resolveManualBinary(binary, it) } ?: binary }
                     ?: nodePackage?.let(::executablePath)
    if (executable == null || root == null) {
      thisLogger().debug(
        "Not starting ${definition.displayName} for ${file.path}: package=$nodePackage, executable=$executable, root=$root")
      return null
    }
    return ServerLaunch(executable, SERVER_PARAMETERS, root)
  }

  private fun manualBinary(): String? {
    val settings = definition.settings(project)
    return settings.binaryPath?.trim()
      ?.takeIf { settings.configurationMode == ConfigurationMode.MANUAL && it.isNotEmpty() }
  }

  /**
   * Where the server runs from: the folder holding the tool's `node_modules`, or for a [manual] binary
   * without one, the file's content root. A file outside the content falls back to the project's folder,
   * because a project opened in Safe Mode has no content until it's reopened, even once trusted.
   */
  private fun serverRoot(file: VirtualFile, nodePackage: NodePackage?, manual: Boolean): VirtualFile? =
    nodePackage?.let(::serverRoot)
    ?: if (manual) contentRootOrProjectFolder(file) else null

  private fun contentRootOrProjectFolder(file: VirtualFile): VirtualFile? =
    ProjectFileIndex.getInstance(project).getContentRootForFile(file)
    ?: project.guessProjectDir()?.takeIf { VfsUtilCore.isAncestor(it, file, false) }

  /** Prefers the nearest `node_modules` above the file, then falls back to a default package. */
  private fun find(file: VirtualFile): NodePackage? {
    val descriptor = NodePackageDescriptor(definition.packageName)
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter

    return descriptor.listAvailable(project, interpreter, file, false, true).firstOrNull()
           ?: descriptor.findUnambiguousDependencyPackage(project)
           ?: NodePackage.findDefaultPackage(project, definition.packageName, interpreter)
  }

  private fun executablePath(nodePackage: NodePackage): String? =
    nodePackage.getAbsolutePackagePathToRequire(project)
      ?.let { Paths.get(it, "bin", definition.packageName).toString() }

  /**
   * The folder containing the `node_modules` that holds the package. Files that resolve to the
   * same package share one server, so a hoisted monorepo runs one server from its root.
   */
  private fun serverRoot(nodePackage: NodePackage): VirtualFile? =
    packageFolder(nodePackage)?.parent?.parent

  /** The installed package's own folder. */
  private fun packageFolder(nodePackage: NodePackage): VirtualFile? =
    LocalFileSystem.getInstance().findFileByPath(nodePackage.systemIndependentPath)

  private companion object {
    val SERVER_PARAMETERS = listOf("--lsp")
  }
}

/**
 * A manual binary written relative to the server's [root], such as `node_modules/.bin/oxlint` or
 * `tools/oxlint.exe`, as a full path. Left as it is, a Docker, SSH or WSL interpreter would read it from
 * the IDE's own folder, and Windows would look for a `.exe` there. A bare name with no file of that name
 * in the root, such as `oxlint`, stays a command for the PATH lookup, and an absolute path is unchanged.
 */
internal fun resolveManualBinary(path: String, root: VirtualFile): String {
  val normalized = PathUtil.toSystemIndependentName(path)
  if (OSAgnosticPathUtil.isAbsolute(normalized)) return path
  val inRoot = '/' in normalized || root.findFileByRelativePath(normalized)?.takeIf { !it.isDirectory } != null
  if (!inRoot) return path
  return Paths.get(root.path).resolve(normalized).normalize().toString()
}
