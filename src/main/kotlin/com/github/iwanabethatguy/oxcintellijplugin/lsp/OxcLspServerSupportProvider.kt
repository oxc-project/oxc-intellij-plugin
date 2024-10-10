package com.github.iwanabethatguy.oxcintellijplugin.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider

@Suppress("UnstableApiUsage")
class OxcLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(project: Project, file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (!OxcLspServerDescriptor.supportedFile(file)) {
            return
        }

        serverStarter.ensureServerStarted(OxcLspServerDescriptor(project))
    }
}
