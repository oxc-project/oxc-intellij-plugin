package com.github.iwanabethatguy.oxcintellijplugin.lsp
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
class OxcLspServerSupportProvider: LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (!(file.extension == "js" ||  file.extension == "jsx" || file.extension == "ts" || file.extension == "tsx")) return


        serverStarter.ensureServerStarted(OxcLspServerDescriptor(project))
    }
}




