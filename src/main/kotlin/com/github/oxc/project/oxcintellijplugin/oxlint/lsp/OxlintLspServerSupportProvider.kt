package com.github.oxc.project.oxcintellijplugin.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import com.intellij.openapi.roots.ModuleRootManager

class OxlintLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        thisLogger().debug("Handling fileOpened for ${file.path}")

        if (!OxlintSettings.getInstance(project).fileSupported(file)) {
            return
        }

        val oxc = OxlintPackage(project)
        if (!oxc.isEnabled()) {
            return
        }
        val executable = oxc.binaryPath(file) ?: return
        val projectRootManager = ProjectRootManager.getInstance(project)
        val root = projectRootManager.fileIndex.getContentRootForFile(file)
            ?: projectRootManager.fileIndex.getModuleForFile(file)?.let {
                ModuleRootManager.getInstance(it).contentRoots.firstOrNull()
            }
            ?: return

        serverStarter.ensureServerStarted(OxlintLspServerDescriptor(project, root, executable, oxc.binaryParameters(file)))
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound, OxlintConfigurable::class.java)
    }
}
