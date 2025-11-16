package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.settings.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.SemVer
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

    fun binaryParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        val settings = OxcSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> emptyList()
            ConfigurationMode.AUTOMATIC -> {
                findOxcParameters(virtualFile)
            }
            ConfigurationMode.MANUAL -> {
                if (settings.binaryPath.isBlank()) {
                    findOxcParameters(virtualFile)
                } else {
                    settings.binaryParameters.map { ProcessCommandParameter.Value(it) }
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        val settings = OxcSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxcExecutable(virtualFile: VirtualFile): String? {
        val oxlintPackage = getPackage(virtualFile) ?: return null
        val path = oxlintPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            val version = oxlintPackage.getVersion(project)

            return if (version?.isGreaterOrEqualThan(OXLINT_FIRST_LSP_VERSION) == true) {
                Paths.get(path, "bin/oxlint").toString()
            } else {
                Paths.get(path, "bin/oxc_language_server").toString()
            }
        }

        return null
    }

    private fun findOxcParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        val oxlintPackage = getPackage(virtualFile) ?: return emptyList()
        val version = oxlintPackage.getVersion(project)

        return if (version?.isGreaterOrEqualThan(OXLINT_FIRST_LSP_VERSION) == true) {
            listOf(ProcessCommandParameter.Value("--lsp"))
        } else {
            emptyList()
        }
    }

    companion object {
        const val CONFIG_NAME = ".oxlintrc"
        val OXLINT_FIRST_LSP_VERSION = SemVer("1.29.0", 1, 29, 0)
        val configValidExtensions = listOf("json")
    }
}
