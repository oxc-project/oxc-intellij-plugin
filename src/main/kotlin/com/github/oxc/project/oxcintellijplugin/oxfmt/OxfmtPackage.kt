package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.OxcServerCommand
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusPackage
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

class OxfmtPackage(
    private val project: Project,
    private val vitePlus: VitePlusPackage = VitePlusPackage(project)
) {
    fun getPackage(virtualFile: VirtualFile?): NodePackage? {
        if (virtualFile != null) {
            val available = NODE_PACKAGE_DESCRIPTOR.listAvailable(
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

        val pkg = NODE_PACKAGE_DESCRIPTOR.findUnambiguousDependencyPackage(project)
                  ?: NodePackage.findDefaultPackage(
                      project,
                      PACKAGE_NAME,
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

    fun resolveCommand(file: VirtualFile): OxcServerCommand? {
        val settings = OxfmtSettings.getInstance(project)
        if (settings.configurationMode == ConfigurationMode.DISABLED) return null
        val manualBinary = settings.configurationMode == ConfigurationMode.MANUAL && settings.binaryPath.isNotBlank()
        val viteProject = if (manualBinary) null else vitePlus.detect(file, settings.binarySource, settings.vitePlusPath)
        if (viteProject != null) {
            return vitePlus.createServerCommand(viteProject, "fmt", settings.vitePlusPath)
        }
        val nodePackage = if (manualBinary) null else getPackage(file)
        val executable = if (manualBinary) settings.binaryPath else nodePackage?.let(::findOxfmtExecutable) ?: return null
        val root = OxcServerCommand.findRoot(project, file, nodePackage) ?: return null
        return OxcServerCommand(executable, listOf("--lsp"), root)
    }

    fun isEnabled(): Boolean {
        val settings = OxfmtSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxfmtExecutable(oxfmtPackage: NodePackage): String? {
        val path = oxfmtPackage.getAbsolutePackagePathToRequire(project) ?: return null
        return Paths.get(path, "bin/oxfmt").toString()
    }

    companion object {

        const val PACKAGE_NAME = "oxfmt"
        val NODE_PACKAGE_DESCRIPTOR = NodePackageDescriptor(PACKAGE_NAME)
        val EMPTY_NODE_PACKAGE = NODE_PACKAGE_DESCRIPTOR.createPackage("")

        const val CONFIG_NAME = ".oxfmtrc"
        const val CONFIG_TS_NAME = "oxfmt.config.ts"
        val CONFIG_VALID_JSON_EXTENSIONS = listOf("json", "jsonc")
    }
}
