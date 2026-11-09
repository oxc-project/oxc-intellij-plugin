package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpManager
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpNodePackage
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import java.nio.file.Paths

/**
 * What to run for a file, and where its server should run from. [vitePlusFolder] is the `vite-plus`
 * package that [executable] belongs to, when the server is `vp`.
 */
data class ServerLaunch(
  val executable: String,
  val parameters: List<String>,
  val root: VirtualFile,
  val vitePlusFolder: String? = null,
)

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

  /**
   * The JSON schema that ships with the tool that would run for [file], so that it matches the tool's
   * version. Under Vite+ that's the tool `vp` runs, and for a manual binary it's the package the binary is
   * in. Returns null if there's no installed package to take it from.
   */
  fun findSchema(file: VirtualFile): VirtualFile? {
    val manualBinary = manualBinary()
    val folder = when {
      manualBinary != null -> {
        val root = serverRoot(file, find(file), manual = true)
        packageContaining(root?.let { resolveManualBinary(manualBinary, it) } ?: manualBinary)
      }
      else -> findVitePlus(file)?.let { toolUnderVitePlus(it) } ?: find(file)?.let(::packageFolder)
    }
    return folder?.findChild(SCHEMA_FILE_NAME)?.takeIf { !it.isDirectory }
  }

  private fun manualBinary(): String? {
    val settings = definition.settings(project)
    return settings.binaryPath?.trim()
      ?.takeIf { settings.configurationMode == ConfigurationMode.MANUAL && it.isNotEmpty() }
      ?.let { resolveSettingPath(project, it) }
  }

  /**
   * Where the server runs from: the folder holding the tool's `node_modules`, or for a [manual] binary
   * without one, the file's content root.
   */
  private fun serverRoot(file: VirtualFile, nodePackage: NodePackage?, manual: Boolean): VirtualFile? =
    nodePackage?.let(::serverRoot)
    ?: ProjectFileIndex.getInstance(project).getContentRootForFile(file).takeIf { manual }

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
    val folder = nodePackage.getAbsolutePackagePathToRequire(project) ?: return null
    val executable = Paths.get(folder, "bin", "vp").toString()
    val root = serverRoot(nodePackage) ?: return null
    return VitePlus(nodePackage, ServerLaunch(executable, listOf(definition.vitePlusCommand, "--lsp"), root, folder))
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

  /**
   * The installed package's own folder. Under Yarn PnP that's Yarn's copy of the package that the
   * workspace depends on: an unplugged folder, or a folder inside a zip in Yarn's cache.
   */
  private fun packageFolder(nodePackage: NodePackage): VirtualFile? =
    if (nodePackage is YarnPnpNodePackage) {
      nodePackage.getPackageJson(project)
        ?.let { YarnPnpManager.getInstance(project).findInstalledPackageDir(it, nodePackage.name) }
    } else {
      LocalFileSystem.getInstance().findFileByPath(nodePackage.systemIndependentPath)
    }

  /**
   * The tool package that `vp` runs. It asks Node for the tool from the server's folder, and then from
   * its own, so the project's copy wins over the one `vite-plus` depends on.
   */
  private fun toolUnderVitePlus(vitePlus: VitePlus): VirtualFile? {
    val nodePackage = vitePlus.nodePackage
    if (nodePackage is YarnPnpNodePackage) return pnpToolUnderVitePlus(nodePackage)
    return nodeModulesLookup(vitePlus.launch.root)
           ?: packageFolder(nodePackage)?.canonicalFile?.let(::nodeModulesLookup)
  }

  /**
   * The same lookup under Yarn PnP, which only resolves declared dependencies: the server's workspace's
   * own dependency on the tool, and then `vite-plus`'s. A workspace doesn't fall back to the root's.
   */
  private fun pnpToolUnderVitePlus(vitePlus: YarnPnpNodePackage): VirtualFile? {
    val packageJson = vitePlus.getPackageJson(project) ?: return null
    val pnp = YarnPnpManager.getInstance(project)
    return pnp.findInstalledPackageDir(packageJson, definition.packageName)
           ?: pnp.findDependency(packageJson, VITE_PLUS_PACKAGE)
             ?.findChildDependencyByName(definition.packageName)
             ?.resolveLocation()
  }

  /** Node's lookup: `node_modules/<name>` in the folder and each one above it. */
  private fun nodeModulesLookup(start: VirtualFile): VirtualFile? =
    generateSequence(start) { it.parent }
      .filter { it.name != NODE_MODULES }
      .firstNotNullOfOrNull { folder ->
        folder.findChild(NODE_MODULES)?.findChild(definition.packageName)?.takeIf { it.isDirectory }
      }

  /**
   * The package a manual binary belongs to, such as `node_modules/oxlint/bin/oxlint`. The nearest
   * `package.json` above the binary has to be the tool's, so a binary dropped into a project doesn't
   * pick up the project's own `package.json`.
   */
  private fun packageContaining(binary: String): VirtualFile? {
    val file = LocalFileSystem.getInstance().findFileByPath(binary)?.canonicalFile ?: return null
    val packageJson = generateSequence(file.parent) { it.parent }
                        .firstNotNullOfOrNull { it.findChild(PackageJsonUtil.FILE_NAME) } ?: return null
    return packageJson.parent.takeIf { PackageJsonData.getOrCreate(packageJson).name == definition.packageName }
  }

  private companion object {
    val SERVER_PARAMETERS = listOf("--lsp")
    const val VITE_PLUS_PACKAGE = "vite-plus"
    const val NODE_MODULES = "node_modules"
    const val SCHEMA_FILE_NAME = "configuration_schema.json"
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
