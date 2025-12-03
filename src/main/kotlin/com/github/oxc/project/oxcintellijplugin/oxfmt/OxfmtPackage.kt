package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

class OxfmtPackage(private val project: Project) {

    private val packageName = "oxfmt"
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

    fun configPath(): String? {
        val settings = OxfmtSettings.getInstance(project)
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
        val settings = OxfmtSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> findOxfmtExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank {
                findOxfmtExecutable(virtualFile)
            }
        }
    }

    fun binaryParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        return findOxfmtParameters(virtualFile)
    }

    fun isEnabled(): Boolean {
        val settings = OxfmtSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxfmtExecutable(virtualFile: VirtualFile): String? {
        val oxfmtPackage = getPackage(virtualFile) ?: return null
        val path = oxfmtPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/oxfmt").toString()
        }

        return null
    }

    private fun findOxfmtParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        return listOf(ProcessCommandParameter.Value("--lsp"))
    }

    companion object {

        const val CONFIG_NAME = ".oxfmtrc"
        val CONFIG_VALID_EXTENSIONS = listOf("json", "jsonc")
    }
}
