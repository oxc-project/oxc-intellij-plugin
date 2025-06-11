package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.OxcPackage
import com.github.oxc.project.oxcintellijplugin.extensions.findNearestOxcConfig
import com.github.oxc.project.oxcintellijplugin.extensions.findNearestPackageJson
import com.github.oxc.project.oxcintellijplugin.settings.OxcConfigurable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import java.io.File

@Suppress("UnstableApiUsage")
class OxcLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        thisLogger().debug("Handling fileOpened for ${file.path}")

        val oxc = OxcPackage(project)
        if (!oxc.isEnabled()) {
            return
        }
        val configPath = oxc.configPath()
        val executable = oxc.binaryPath(file) ?: return

        val projectRootDir = project.guessProjectDir() ?: return
        val root: VirtualFile
        if (configPath?.isNotEmpty() == true) {
            val configVirtualFile = VirtualFileManager.getInstance().findFileByNioPath(File(configPath).toPath()) ?: return
            root = configVirtualFile.findNearestPackageJson(projectRootDir)?.parent ?: return
        } else {
            root = file.findNearestOxcConfig(projectRootDir)?.parent ?: projectRootDir
        }

        serverStarter.ensureServerStarted(OxcLspServerDescriptor(project, root, executable))
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem? {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound, OxcConfigurable::class.java)
    }
}
