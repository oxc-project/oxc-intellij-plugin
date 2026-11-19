package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

/** What to run for a file, and where its server should run from. */
data class ServerLaunch(
  val executable: String,
  val parameters: List<String>,
  val root: VirtualFile,
)

/** Finds what to run for a file: the tool's package. */
class ToolPackage(private val project: Project, private val definition: ToolDefinition) {

  /** Returns null if there's nothing to run. */
  fun resolve(file: VirtualFile): ServerLaunch? {
    val nodePackage = find(file)
    val root = nodePackage?.let(::serverRoot)
    val executable = nodePackage?.let(::executablePath)
    if (executable == null || root == null) {
      thisLogger().debug(
        "Not starting ${definition.displayName} for ${file.path}: package=$nodePackage, executable=$executable, root=$root")
      return null
    }
    return ServerLaunch(executable, SERVER_PARAMETERS, root)
  }

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
