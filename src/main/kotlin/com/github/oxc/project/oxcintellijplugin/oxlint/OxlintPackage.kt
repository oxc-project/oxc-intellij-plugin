package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusPackage
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.SemVer
import java.nio.file.Paths

class OxlintPackage(
    private val project: Project,
    private val vitePlus: VitePlusPackage = VitePlusPackage(project)
) {
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
        val settings = OxlintSettings.getInstance(project)
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
        val settings = OxlintSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        // We need to prefer `vite-plus` over `oxlint` because it may be also available as npm hoists it.
        // It can't detect the `vite.config.ts` configuration if we prefer the dedicated package instead.
        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> vitePlus.findOxlintExecutable(virtualFile) ?: findOxlintExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank {
                vitePlus.findOxlintExecutable(virtualFile) ?: findOxlintExecutable(virtualFile)
            }
        }
    }

    fun binaryParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        val settings = OxlintSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> emptyList()
            ConfigurationMode.AUTOMATIC -> {
                findOxlintParameters(virtualFile)
            }
            ConfigurationMode.MANUAL -> {
                if (settings.binaryPath.isBlank()) {
                    findOxlintParameters(virtualFile)
                } else {
                    settings.binaryParameters.map { ProcessCommandParameter.Value(it) }
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        val settings = OxlintSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxlintExecutable(virtualFile: VirtualFile): String? {
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

    private fun findOxlintParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        val vitePlusPackage = vitePlus.getPackage(virtualFile)
        if (vitePlusPackage != null) {
            return listOf(ProcessCommandParameter.Value("--lsp"))
        }

        val oxlintPackage = getPackage(virtualFile)
        if (oxlintPackage != null) {
            val version = oxlintPackage.getVersion(project)

            return if (version?.isGreaterOrEqualThan(OXLINT_FIRST_LSP_VERSION) == true) {
                listOf(ProcessCommandParameter.Value("--lsp"))
            } else {
                emptyList()
            }
        }

        return emptyList()
    }

    companion object {
        const val CONFIG_NAME = ".oxlintrc"
        const val CONFIG_TS_NAME = "oxlint.config.ts"
        val OXLINT_FIRST_LSP_VERSION = SemVer("1.29.0", 1, 29, 0)
        val configValidJsonExtensions = listOf("json", "jsonc")
    }
}
