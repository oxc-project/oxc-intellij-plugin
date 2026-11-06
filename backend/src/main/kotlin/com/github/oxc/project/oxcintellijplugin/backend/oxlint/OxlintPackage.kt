package com.github.oxc.project.oxcintellijplugin.backend.oxlint

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpNodePackage
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

private const val PACKAGE_NAME = "oxlint"

/** Finds the `oxlint` package to run for a file, and where its server should run from. */
class OxlintPackage(private val project: Project) {

  /** Prefers the nearest `node_modules` above the file, then falls back to a default package. */
  fun find(file: VirtualFile): NodePackage? {
    val descriptor = NodePackageDescriptor(PACKAGE_NAME)
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter

    return descriptor.listAvailable(project, interpreter, file, false, true).firstOrNull()
           ?: descriptor.findUnambiguousDependencyPackage(project)
           ?: NodePackage.findDefaultPackage(project, PACKAGE_NAME, interpreter)
  }

  fun executablePath(nodePackage: NodePackage): String? =
    nodePackage.getAbsolutePackagePathToRequire(project)
      ?.let { Paths.get(it, "bin", PACKAGE_NAME).toString() }

  /**
   * The folder containing the `node_modules` that holds the package. Files that resolve to the
   * same package share one server, so a hoisted monorepo runs one server from its root.
   */
  fun serverRoot(nodePackage: NodePackage): VirtualFile? =
    if (nodePackage is YarnPnpNodePackage) {
      nodePackage.getPackageJson(project)?.parent
    } else {
      LocalFileSystem.getInstance()
        .findFileByPath(nodePackage.systemIndependentPath)?.parent?.parent
    }
}
