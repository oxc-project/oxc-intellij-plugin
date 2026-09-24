package com.github.oxc.project.oxcintellijplugin.viteplus

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

class VitePlusPackage(private val project: Project) {
    private val packageName = "vite-plus"
    private val packageDescription = NodePackageDescriptor(packageName)

    fun getPackage(virtualFile: VirtualFile?): NodePackage? {
        if (virtualFile != null) {
            val available = packageDescription.listAvailable(
                project,
                NodeJsInterpreterManager.getInstance(project).interpreter,
                virtualFile,
                false,
                true
            )
            if (available.isNotEmpty()) {
                return available[0]
            }
        }

        // Unlike `oxlint` and `oxfmt`, do not fall back to `findDefaultPackage`, which may find a global `vite-plus`.
        // Vite+ should run with the version installed in the project.
        return packageDescription.findUnambiguousDependencyPackage(project)
    }

    /**
     * Returns the `vp` entry of `vite-plus`, launched as `vp lint --lsp` or `vp fmt --lsp`.
     */
    fun findExecutable(vitePlusPackage: NodePackage): String? {
        val path = vitePlusPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/vp").toString()
        }

        return null
    }

    companion object {
        const val CONFIG_NAME = "vite.config"
        val configValidExtensions = listOf("ts", "mts", "cts", "js", "mjs", "cjs")
    }
}
