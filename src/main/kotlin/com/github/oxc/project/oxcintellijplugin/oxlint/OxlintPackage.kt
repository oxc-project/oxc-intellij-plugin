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
        vitePlusPackage: NodePackage?,
    ): String? {
        val settings = OxlintSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        // We need to prefer `vite-plus` over `oxlint` because it may be also available as npm hoists it.
        // It can't detect the `vite.config.ts` configuration if we prefer the dedicated package instead.
        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> vitePlusPackage?.let(vitePlus::findExecutable) ?: findOxlintExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank {
                vitePlusPackage?.let(vitePlus::findExecutable) ?: findOxlintExecutable(virtualFile)
            }
        }
    }

    fun binaryParameters(vitePlusPackage: NodePackage?): List<ProcessCommandParameter> {
        if (vitePlusPackage != null) {
            return listOf(ProcessCommandParameter.Value("lint"), ProcessCommandParameter.Value("--lsp"))
        }
        return listOf(ProcessCommandParameter.Value("--lsp"))
    }

    /**
     * Returns `vite-plus` to launch `vp lint --lsp`, unless a binary is set manually.
     * Resolve it once per file and pass it to [binaryPath] and [binaryParameters].
     */
    fun vitePlusPackage(virtualFile: VirtualFile): NodePackage? {
        val settings = OxlintSettings.getInstance(project)
        return when (settings.configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> vitePlus.getPackage(virtualFile)
            ConfigurationMode.MANUAL -> if (settings.binaryPath.isBlank()) vitePlus.getPackage(virtualFile) else null
        }?.takeIf { vitePlus.findExecutable(it) != null }
    }

    fun isEnabled(): Boolean {
        val settings = OxlintSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxlintExecutable(virtualFile: VirtualFile): String? {
        val oxlintPackage = getPackage(virtualFile) ?: return null
        val path = oxlintPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/oxlint").toString()
        }

        return null
    }

    companion object {
        const val CONFIG_NAME = ".oxlintrc"
        const val CONFIG_TS_NAME = "oxlint.config.ts"
        val configValidJsonExtensions = listOf("json", "jsonc")
    }
}
