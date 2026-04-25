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

        val pkg = packageDescription.findUnambiguousDependencyPackage(project)
            ?: NodePackage.findDefaultPackage(
                project,
                packageName,
                NodeJsInterpreterManager.getInstance(project).interpreter
            )

        return pkg
    }

    fun findOxfmtExecutable(virtualFile: VirtualFile): String? {
        val vitePlusPackage = getPackage(virtualFile) ?: return null
        val path = vitePlusPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/oxfmt").toString()
        }

        return null
    }

    fun findOxlintExecutable(virtualFile: VirtualFile): String? {
        val vitePlusPackage = getPackage(virtualFile) ?: return null
        val path = vitePlusPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/oxlint").toString()
        }

        return null
    }

    companion object {
        const val CONFIG_NAME = "vite.config.ts"
        val configValidExtensions = listOf("ts", "mts", "cts", "js", "mjs", "cjs")
    }
}
