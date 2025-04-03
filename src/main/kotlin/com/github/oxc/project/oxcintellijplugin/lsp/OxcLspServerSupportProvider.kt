package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.OxcPackage
import com.github.oxc.project.oxcintellijplugin.extensions.findNearestOxcConfig
import com.github.oxc.project.oxcintellijplugin.settings.OxcConfigurable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

@Suppress("UnstableApiUsage")
class OxcLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        thisLogger().debug("Handling fileOpened for ${file.path}")

        val projectRootDir = project.guessProjectDir() ?: return
        val root = file.findNearestOxcConfig(projectRootDir)?.parent ?: return

        val oxc = OxcPackage(project)
        val configPath = oxc.configPath()
        val executable = oxc.binaryPath(root.path, file) ?: return

        serverStarter.ensureServerStarted(OxcLspServerDescriptor(project, root, executable, configPath))
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem? {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound, OxcConfigurable::class.java)
    }
}
