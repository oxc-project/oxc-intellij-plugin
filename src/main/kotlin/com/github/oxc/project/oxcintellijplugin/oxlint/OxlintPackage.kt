package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.OxcServerCommand
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

    fun resolveCommand(file: VirtualFile): OxcServerCommand? {
        val settings = OxlintSettings.getInstance(project)
        if (settings.configurationMode == ConfigurationMode.DISABLED) return null
        val manualBinary = settings.configurationMode == ConfigurationMode.MANUAL && settings.binaryPath.isNotBlank()
        val viteProject = if (manualBinary) null else vitePlus.detect(file, settings.binarySource, settings.vitePlusPath)
        if (viteProject != null) {
            return vitePlus.createServerCommand(viteProject, "lint", settings.vitePlusPath)
        }
        val nodePackage = if (manualBinary) null else getPackage(file)
        val executable = if (manualBinary) settings.binaryPath else nodePackage?.let(::findOxlintExecutable) ?: return null
        val root = OxcServerCommand.findRoot(project, file, nodePackage) ?: return null
        val arguments = when {
            manualBinary -> settings.binaryParameters.toList()
            nodePackage?.getVersion(project)?.isGreaterOrEqualThan(OXLINT_FIRST_LSP_VERSION) == true -> listOf("--lsp")
            else -> emptyList()
        }
        return OxcServerCommand(executable, arguments, root)
    }

    fun isEnabled(): Boolean {
        val settings = OxlintSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxlintExecutable(oxlintPackage: NodePackage): String? {
        val path = oxlintPackage.getAbsolutePackagePathToRequire(project) ?: return null
        val version = oxlintPackage.getVersion(project)
        val executable = if (version?.isGreaterOrEqualThan(OXLINT_FIRST_LSP_VERSION) == true) {
            "bin/oxlint"
        } else {
            "bin/oxc_language_server"
        }
        return Paths.get(path, executable).toString()
    }

    companion object {
        const val CONFIG_NAME = ".oxlintrc"
        const val CONFIG_TS_NAME = "oxlint.config.ts"
        val OXLINT_FIRST_LSP_VERSION = SemVer("1.29.0", 1, 29, 0)
        val configValidJsonExtensions = listOf("json", "jsonc")
    }
}
