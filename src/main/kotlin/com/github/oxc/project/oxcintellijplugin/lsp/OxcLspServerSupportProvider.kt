package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsComponent
import com.intellij.openapi.components.service
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
        if (!project.service<OxcSettingsComponent>().enable) {
            return
        }

        serverStarter.ensureServerStarted(OxcLspServerDescriptor(project))
    }
}
