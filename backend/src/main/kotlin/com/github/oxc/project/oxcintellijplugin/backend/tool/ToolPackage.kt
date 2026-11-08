package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
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

/**
 * Finds what to run for a file: the project's Vite+, else the tool's package, or the manual path.
 *
 * Vite+ comes first even when the tool's own package is installed too, because npm hoists the tool out of
 * `vite-plus`, and only `vp` reads `vite.config.*`. A manual binary never uses Vite+.
 */
class ToolPackage(private val project: Project, private val definition: ToolDefinition) {

  /** Returns null if there's nothing to run. */
  fun resolve(file: VirtualFile): ServerLaunch? {
    val manualBinary = manualBinary()
    if (manualBinary == null) {
      findVitePlus(file)?.let { return it.launch }
    }

    val nodePackage = find(file)
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

  private fun manualBinary(): String? {
    val settings = definition.settings(project)
    return settings.binaryPath?.trim()
      ?.takeIf { settings.configurationMode == ConfigurationMode.MANUAL && it.isNotEmpty() }
      ?.let { resolveSettingPath(project, it) }
  }

  private class VitePlus(val nodePackage: NodePackage, val launch: ServerLaunch)

  /**
   * The project's own `vite-plus`, with no global fallback, so the version the project chose always runs.
   * Whether `bin/vp` exists isn't checked, the same as v0.0.45.
   */
  private fun findVitePlus(file: VirtualFile): VitePlus? {
    val descriptor = NodePackageDescriptor(VITE_PLUS_PACKAGE)
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
    val nodePackage = descriptor.listAvailable(project, interpreter, file, false, true).firstOrNull()
                      ?: descriptor.findUnambiguousDependencyPackage(project)
                      ?: return null
    val executable = nodePackage.getAbsolutePackagePathToRequire(project)
                       ?.let { Paths.get(it, "bin", "vp").toString() } ?: return null
    val root = serverRoot(nodePackage) ?: return null
    return VitePlus(nodePackage, ServerLaunch(executable, listOf(definition.vitePlusCommand, "--lsp"), root))
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
      packageFolder(nodePackage)?.parent?.parent
    }

  private fun packageFolder(nodePackage: NodePackage): VirtualFile? =
    if (nodePackage is YarnPnpNodePackage) {
      nodePackage.getPackageJson(project)?.parent
    } else {
      LocalFileSystem.getInstance().findFileByPath(nodePackage.systemIndependentPath)
    }

  private companion object {
    val SERVER_PARAMETERS = listOf("--lsp")
    const val VITE_PLUS_PACKAGE = "vite-plus"
  }
}
