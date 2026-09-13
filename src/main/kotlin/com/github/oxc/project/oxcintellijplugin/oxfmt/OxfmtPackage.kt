package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.OxcServerCommand
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusNotifications
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusPackage
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
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
        val manual = settings.configurationMode == ConfigurationMode.MANUAL && settings.binaryPath.isNotBlank()
        if (!manual) {
            val viteProject = vitePlus.detect(file, settings.binarySource, settings.vitePlusPath)
            if (viteProject != null) {
                val notifications = VitePlusNotifications.getInstance(project)
                val executable = viteProject.vpPath
                if (executable == null) {
                    notifications.unavailable(viteProject.root.toString(), settings.vitePlusPath)
                    return null
                }
                notifications.resolved(viteProject.root.toString(), settings.vitePlusPath)
                val root = VirtualFileManager.getInstance().findFileByNioPath(viteProject.root) ?: return null
                return OxcServerCommand(executable.toString(), listOf("fmt", "--lsp"), root, vitePlus = true)
            }
        }
        val nodePackage = if (manual) null else getPackage(file)
        val executable = if (manual) settings.binaryPath else nodePackage?.let(::findOxfmtExecutable) ?: return null
        val root = OxcServerCommand.findRoot(project, file, nodePackage) ?: return null
        val arguments = listOf("--lsp")
        return OxcServerCommand(executable, arguments, root)
    }

    fun isEnabled(): Boolean {
        val settings = OxfmtSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxfmtExecutable(oxfmtPackage: NodePackage): String? {
        val path = oxfmtPackage.getAbsolutePackagePathToRequire(project)
        if (path != null) {
            return Paths.get(path, "bin/oxfmt").toString()
        }

        return null
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
