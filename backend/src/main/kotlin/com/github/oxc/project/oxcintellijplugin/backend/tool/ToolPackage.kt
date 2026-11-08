package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpNodePackage
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

/** What to run for a file, and where its server should run from. */
data class ServerLaunch(val executable: String, val parameters: List<String>, val root: VirtualFile)

/** Finds the tool's package for a file, or uses the manual path. */
class ToolPackage(private val project: Project, private val definition: ToolDefinition) {

  /** Returns null if there's nothing to run. Vite+ will be another package to look for here. */
  fun resolve(file: VirtualFile): ServerLaunch? {
    val settings = definition.settings(project)
    val nodePackage = find(file)
    val manualBinary = settings.binaryPath?.takeIf {
      settings.configurationMode == ConfigurationMode.MANUAL && it.isNotBlank()
    }
    val executable = manualBinary ?: nodePackage?.let(::executablePath)
    val root = nodePackage?.let(::serverRoot)
               ?: ProjectFileIndex.getInstance(project).getContentRootForFile(file).takeIf { manualBinary != null }
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
    if (nodePackage is YarnPnpNodePackage) {
      nodePackage.getPackageJson(project)?.parent
    } else {
      LocalFileSystem.getInstance()
        .findFileByPath(nodePackage.systemIndependentPath)?.parent?.parent
    }

  private companion object {
    val SERVER_PARAMETERS = listOf("--lsp")
  }
}
