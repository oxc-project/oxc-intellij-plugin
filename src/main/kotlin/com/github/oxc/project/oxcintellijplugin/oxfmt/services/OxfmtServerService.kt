package com.github.oxc.project.oxcintellijplugin.oxfmt.services

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.lsp.LspServerRestartPolicy
import com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerRestartListener
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.application.options.CodeStyle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

@Service(Service.Level.PROJECT)
class OxfmtServerService(private val project: Project) {

    private val PROVIDER_CLASS = OxfmtLspServerSupportProvider::class.java
    private val GROUP_ID = "Oxc"

    private val restartPolicy = LspServerRestartPolicy()

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
        val indentOptions = ReadAction.compute<IndentOptions, Throwable> {
            val codeStyleSettings = CodeStyle.getSettings(project, document)
            return@compute codeStyleSettings.getIndentOptionsByDocument(project, document)
        }

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
                        endLineOffset + it.range.end.character,
                        it.newText.lines().joinToString(separator = "\n"))
                }
            })
    }

    /** Supervises one server; the descriptor of every started server needs its own listener. */
    fun createRestartListener(): LspServerListener =
        OxcLspServerRestartListener("Oxfmt", restartPolicy,
            isToolEnabled = { OxfmtSettings.getInstance(project).isEnabled() },
            // Leaves the stop path the decision is taken in, and drops a restart that a project
            // being closed no longer needs.
            requestRestart = {
                ApplicationManager.getApplication().invokeLater(::restartServer, project.disposed)
            })

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
