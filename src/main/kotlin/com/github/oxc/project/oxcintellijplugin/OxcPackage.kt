package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.settings.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

class OxcPackage(private val project: Project) {
    private val packageName = "oxlint"
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

        val pkg = packageDescription.findUnambiguousDependencyPackage(project) ?: NodePackage.findDefaultPackage(
            project,
            packageName,
            NodeJsInterpreterManager.getInstance(project).interpreter
        )

        return pkg
    }

    fun configPath(): String? {
        val settings = OxcSettings.getInstance(project)
        val configurationMode = settings.configurationMode
        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> null
            ConfigurationMode.MANUAL -> settings.configPath
        }
    }

    fun binaryPath(
        virtualFile: VirtualFile,
    ): String? {
        val settings = OxcSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> findOxcExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank { findOxcExecutable(virtualFile) }
        }
    }

    fun isEnabled(): Boolean {
        val settings = OxcSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxcExecutable(virtualFile: VirtualFile?): String? {
        val path = getPackage(virtualFile)?.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/oxc_language_server").toString()
        }

        return null
    }

    companion object {
        const val CONFIG_NAME = ".oxlintrc"
        val configValidExtensions = listOf("json")
    }
}
