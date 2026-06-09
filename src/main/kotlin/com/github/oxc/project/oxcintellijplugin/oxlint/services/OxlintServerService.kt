package com.github.oxc.project.oxcintellijplugin.oxlint.services

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.util.getLsp4jRange
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.TextEdit

@Service(Service.Level.PROJECT)
class OxlintServerService(private val project: Project) {
    private val groupId = "Oxc"

    companion object {
        fun getInstance(project: Project): OxlintServerService = project.getService(OxlintServerService::class.java)
    }

    private fun getServer(file: VirtualFile) =
        LspServerManager.getInstance(project).getServersForProvider(OxlintLspServerSupportProvider::class.java)
            .firstOrNull { server -> server.descriptor.isSupportedFile(file) }

    suspend fun fixAll(document: Document): Boolean {
        val manager = FileDocumentManager.getInstance()
        val file = manager.getFile(document) ?: return false

        return fixAll(file, document)
    }

    suspend fun fixAll(file: VirtualFile, document: Document): Boolean {
        val server = getServer(file) ?: return false

        val commandName = OxlintBundle.message("oxlint.run.quickfix")
        val fixKind = OxlintSettings.getInstance(project).fixKind
        if (fixKind == OxlintFixKind.NONE) {
            return false
        }
        val codeActionKinds = when {
            fixKind.includesSuggestions() -> listOf(CodeActionKind.QuickFix)
            fixKind.isDangerous() -> listOf("source.fixAllDangerous.oxc")
            else -> listOf("source.fixAll.oxc")
        }

        val codeActionParams = CodeActionParams(server.getDocumentIdentifier(file),
            getLsp4jRange(document, 0, document.textLength),
            CodeActionContext().apply {
                diagnostics = emptyList()
                only = codeActionKinds
                triggerKind = CodeActionTriggerKind.Invoked
            })

        val codeActionResults = server.sendRequest { it.textDocumentService.codeAction(codeActionParams) }
        val edits = codeActionResults.orEmpty()
            .filter { it.isRight && it.right.isPreferred }
            .flatMap { it.right.edit?.changes?.get(server.getDocumentIdentifier(file).uri).orEmpty() }

        WriteCommandAction.runWriteCommandAction(project, commandName, groupId, {
            applyTextEdits(document, edits)
        })

        return edits.isNotEmpty()
    }

    private fun applyTextEdits(document: Document, edits: List<TextEdit>) {
        edits.sortedWith(compareByDescending<TextEdit> { it.range.start.line }
            .thenByDescending { it.range.start.character })
            .forEach {
                val startLineOffset = document.getLineStartOffset(it.range.start.line)
                val endLineOffset = document.getLineStartOffset(it.range.end.line)
                document.replaceString(startLineOffset + it.range.start.character,
                    endLineOffset + it.range.end.character,
                    it.newText.lines().joinToString(separator = "\n"))
            }
    }

    fun restartServer() {
        LspServerManager.getInstance(project).stopAndRestartIfNeeded(OxlintLspServerSupportProvider::class.java)
    }

    fun stopServer() {
        LspServerManager.getInstance(project).stopServers(OxlintLspServerSupportProvider::class.java)
    }

    fun notifyRestart() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                OxlintBundle.message("oxlint.language.server.restarted"),
                "",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
