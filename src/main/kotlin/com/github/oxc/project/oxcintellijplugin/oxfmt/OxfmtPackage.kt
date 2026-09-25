package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
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

    fun binaryPath(
        virtualFile: VirtualFile,
        vitePlusPackage: NodePackage?,
    ): String? {
        val settings = OxfmtSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        // We need to prefer `vite-plus` over `oxfmt` because it may be also available as npm hoists it.
        // It can't detect the `vite.config.ts` configuration if we prefer the dedicated package instead.
        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> vitePlusPackage?.let(vitePlus::findExecutable) ?: findOxfmtExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank {
                vitePlusPackage?.let(vitePlus::findExecutable) ?: findOxfmtExecutable(virtualFile)
            }
        }
    }

    fun binaryParameters(virtualFile: VirtualFile, vitePlusPackage: NodePackage?): List<ProcessCommandParameter> {
        return findOxfmtParameters(virtualFile, vitePlusPackage)
    }

    /**
     * Returns `vite-plus` to launch `vp fmt --lsp`, unless a binary is set manually.
     * Resolve it once per file and pass it to [binaryPath] and [binaryParameters].
     */
    fun vitePlusPackage(virtualFile: VirtualFile): NodePackage? {
        val settings = OxfmtSettings.getInstance(project)
        return when (settings.configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> vitePlus.getPackage(virtualFile)
            ConfigurationMode.MANUAL -> if (settings.binaryPath.isBlank()) vitePlus.getPackage(virtualFile) else null
        }?.takeIf { vitePlus.findExecutable(it) != null }
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

    private fun findOxfmtParameters(virtualFile: VirtualFile, vitePlusPackage: NodePackage?): List<ProcessCommandParameter> {
        if (vitePlusPackage != null) {
            return listOf(ProcessCommandParameter.Value("fmt"), ProcessCommandParameter.Value("--lsp"))
        }
        return listOf(ProcessCommandParameter.Value("--lsp"))
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
