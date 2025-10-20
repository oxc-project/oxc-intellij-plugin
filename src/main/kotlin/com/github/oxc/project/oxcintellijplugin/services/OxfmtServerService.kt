package com.github.oxc.project.oxcintellijplugin.services

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.github.oxc.project.oxcintellijplugin.lsp.OxfmtLspServerSupportProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

@Service(Service.Level.PROJECT)
class OxfmtServerService(private val project: Project) {

    private val groupId = "Oxc"

    companion object {

        fun getInstance(project: Project): OxfmtServerService =
            project.getService(OxfmtServerService::class.java)
    }

    private fun getServer(file: VirtualFile) =
        LspServerManager.getInstance(project)
            .getServersForProvider(OxfmtLspServerSupportProvider::class.java)
            .firstOrNull { server -> server.descriptor.isSupportedFile(file) }

    suspend fun fixAll(document: Document) {
        val manager = FileDocumentManager.getInstance()
        val file = manager.getFile(document) ?: return

        fixAll(file, document)
    }

    suspend fun fixAll(file: VirtualFile, document: Document) {
        val server = getServer(file) ?: return

        val commandName = OxcBundle.message("oxlint.run.quickfix")

        val formattingParams = DocumentFormattingParams(server.getDocumentIdentifier(file),
            FormattingOptions(2, true))

        val formattingResults = server.sendRequest {
            it.textDocumentService.formatting(formattingParams)
        }

        WriteCommandAction.runWriteCommandAction(project, commandName, groupId, {
            formattingResults?.forEach {
                val startOffset = document.getLineStartOffset(it.range.start.line) + it.range.start.character
                val endOffset = document.getLineStartOffset(it.range.end.line) + it.range.end.character

                document.replaceString(startOffset, endOffset, it.newText)
            }
        })
    }

    fun restartServer() {
        LspServerManager.getInstance(project)
            .stopAndRestartIfNeeded(OxfmtLspServerSupportProvider::class.java)
    }

    fun stopServer() {
        LspServerManager.getInstance(project).stopServers(OxfmtLspServerSupportProvider::class.java)
    }

    fun notifyRestart() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Oxc")
            .createNotification(
                OxcBundle.message("oxlint.language.server.restarted"),
                "",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
