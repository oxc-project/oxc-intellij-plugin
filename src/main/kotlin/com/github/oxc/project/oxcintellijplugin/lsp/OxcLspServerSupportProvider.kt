package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.OxcPackage
import com.github.oxc.project.oxcintellijplugin.settings.OxcConfigurable
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import kotlin.io.path.Path

class OxcLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        thisLogger().debug("Handling fileOpened for ${file.path}")

        if (!OxcSettings.getInstance(project).fileSupported(file)) {
            return
        }

        val oxc = OxcPackage(project)
        if (!oxc.isEnabled()) {
            return
        }
        val executable = oxc.binaryPath(file) ?: return
        val nodePackage = oxc.getPackage(file)
        val root = if (nodePackage != null) {
            VirtualFileManager.getInstance().findFileByNioPath(Path(nodePackage.systemIndependentPath))?.parent?.parent ?: return
        } else {
            ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
        }

        serverStarter.ensureServerStarted(OxcLspServerDescriptor(project, root, executable, oxc.binaryParameters(file)))
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound, OxcConfigurable::class.java)
    }
}
