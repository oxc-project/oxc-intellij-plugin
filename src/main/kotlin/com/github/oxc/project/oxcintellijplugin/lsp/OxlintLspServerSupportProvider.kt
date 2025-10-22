package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.settings.OxlintSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import kotlin.io.path.Path

class OxlintLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        thisLogger().debug("Handling fileOpened for ${file.path}")

        if (!OxlintSettings.getInstance(project).fileSupported(file)) {
            return
        }

        val oxlintPackage = OxlintPackage(project)
        if (!oxlintPackage.isEnabled()) {
            return
        }
        val executable = oxlintPackage.binaryPath(file) ?: return
        val nodePackage = oxlintPackage.getPackage(file)
        val root = if (nodePackage != null) {
            VirtualFileManager.getInstance().findFileByNioPath(Path(nodePackage.systemIndependentPath))?.parent?.parent ?: return
        } else {
            ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
        }

        serverStarter.ensureServerStarted(OxlintLspServerDescriptor(project, root, executable))
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound, OxlintConfigurable::class.java)
    }
}
