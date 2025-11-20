package com.github.oxc.project.oxcintellijplugin.oxfmt.services

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.intellij.application.options.CodeStyle
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

    private val PROVIDER_CLASS = OxfmtLspServerSupportProvider::class.java
    private val GROUP_ID = "Oxc"

    companion object {

        fun getInstance(project: Project): OxfmtServerService =
            project.getService(OxfmtServerService::class.java)
    }

    private fun getServer(file: VirtualFile) =
        LspServerManager.getInstance(project).getServersForProvider(PROVIDER_CLASS)
            .firstOrNull { server -> server.descriptor.isSupportedFile(file) }

    suspend fun fixAll(document: Document) {
        val manager = FileDocumentManager.getInstance()
        val file = manager.getFile(document) ?: return

        fixAll(file, document)
    }

    suspend fun fixAll(file: VirtualFile, document: Document) {
        val server = getServer(file) ?: return
        val codeStyleSettings = CodeStyle.getSettings(project, document)
        val indentOptions = codeStyleSettings.getIndentOptionsByDocument(project, document)

        val documentFormattingParams = DocumentFormattingParams(server.getDocumentIdentifier(file),
            FormattingOptions(indentOptions.INDENT_SIZE, !indentOptions.USE_TAB_CHARACTER))

        val formattingResults = server.sendRequest {
            it.textDocumentService.formatting(documentFormattingParams)
        }

        WriteCommandAction.runWriteCommandAction(project, OxfmtBundle.message("oxfmt.run.quickfix"),
            GROUP_ID, {
                formattingResults?.forEach {
                    val startLineOffset = document.getLineStartOffset(it.range.start.line)
                    val endLineOffset = document.getLineStartOffset(it.range.end.line)
                    document.replaceString(startLineOffset + it.range.start.character,
                        endLineOffset + it.range.end.character, it.newText)
                }
            })
    }

    fun restartServer() {
        LspServerManager.getInstance(project).stopAndRestartIfNeeded(PROVIDER_CLASS)
    }

    fun stopServer() {
        LspServerManager.getInstance(project).stopServers(PROVIDER_CLASS)
    }

    fun notifyRestart() {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(OxfmtBundle.message("oxfmt.language.server.restarted"), "",
                NotificationType.INFORMATION).notify(project)
    }
}
