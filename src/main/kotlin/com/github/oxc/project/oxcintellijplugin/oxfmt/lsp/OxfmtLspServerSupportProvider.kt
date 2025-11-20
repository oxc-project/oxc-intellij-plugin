package com.github.oxc.project.oxcintellijplugin.oxfmt.lsp

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import kotlin.io.path.Path

class OxfmtLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        thisLogger().debug("Handling fileOpened for ${file.path}")

        if (!OxfmtSettings.getInstance(project).fileSupported(file)) {
            return
        }

        val oxfmt = OxfmtPackage(project)
        if (!oxfmt.isEnabled()) {
            return
        }
        val executable = oxfmt.binaryPath(file) ?: return
        val nodePackage = oxfmt.getPackage(file)
        val root = if (nodePackage != null) {
            VirtualFileManager.getInstance()
                .findFileByNioPath(Path(nodePackage.systemIndependentPath))?.parent?.parent
            ?: return
        } else {
            ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file) ?: return
        }

        serverStarter.ensureServerStarted(
            OxfmtLspServerDescriptor(project, root, executable, oxfmt.binaryParameters(file)))
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound,
            OxfmtConfigurable::class.java)
    }
}
