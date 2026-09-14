package com.github.oxc.project.oxcintellijplugin.oxfmt.lsp

import com.github.oxc.project.oxcintellijplugin.OxcLspServerPool
import com.github.oxc.project.oxcintellijplugin.OxcLspTool
import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

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
        OxcLspServerPool.getInstance(project).fileOpened(OxcLspTool.OXFMT, file, allowEviction = false)
    }

    override fun createLspServerWidgetItem(lspServer: LspServer,
        currentFile: VirtualFile?): LspServerWidgetItem {
        return LspServerWidgetItem(lspServer, currentFile, OxcIcons.OxcRound,
            OxfmtConfigurable::class.java)
    }
}
