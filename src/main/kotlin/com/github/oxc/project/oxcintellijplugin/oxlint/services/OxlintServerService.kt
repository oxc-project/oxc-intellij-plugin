package com.github.oxc.project.oxcintellijplugin.oxlint.services

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.util.getLsp4jRange
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.TextDocumentIdentifier

@Service(Service.Level.PROJECT)
class OxlintServerService(private val project: Project) {
    private val groupId = "Oxc"

    companion object {
        fun getInstance(project: Project): OxlintServerService = project.getService(OxlintServerService::class.java)
    }

    private fun getServer(file: VirtualFile) =
        LspServerManager.getInstance(project).getServersForProvider(OxlintLspServerSupportProvider::class.java)
            .firstOrNull { server -> server.descriptor.isSupportedFile(file) }

    suspend fun fixAll(document: Document) {
        val manager = FileDocumentManager.getInstance()
        val file = manager.getFile(document) ?: return

        fixAll(file, document)
    }

    suspend fun fixAll(file: VirtualFile, document: Document) {
        val server = getServer(file) ?: return

        val commandName = OxlintBundle.message("oxlint.run.quickfix")
        val documentId = server.getDocumentIdentifier(file)

        if (!warmFixCache(server, documentId)) {
            return
        }

        val codeActionParams = CodeActionParams(documentId,
            getLsp4jRange(document, 0, document.textLength),
            CodeActionContext().apply {
                diagnostics = emptyList()
                only = listOf("source.fixAll.oxc")
                // Invoked so a cold cache re-lints instead of returning null.
                triggerKind = CodeActionTriggerKind.Invoked
            })

        val codeActionResults = server.sendRequest { it.textDocumentService.codeAction(codeActionParams) }

        WriteCommandAction.runWriteCommandAction(project, commandName, groupId, {
            codeActionResults?.forEach {
                // Only apply preferred actions which contain real fixes.
                // non-preferred options contain fixes such as disable-next-line.
                if (it.isRight && it.right.isPreferred) {
                    val action = LspIntentionAction(server, it.right)
                    if (action.isAvailable()) {
                        action.invoke(null)
                    }
                }
            }
        })
    }

    /**
     * The server answers `source.fixAll.oxc` from a per-URI cache that only
     * `textDocument/diagnostic` fills and `didChange` (e.g. saving) clears. Warming it up lets
     * the subsequent code action fix the current buffer instead of stale on-disk content.
     */
    private suspend fun warmFixCache(server: LspServer, documentId: TextDocumentIdentifier): Boolean {
        val report = server.sendRequest { it.textDocumentService.diagnostic(DocumentDiagnosticParams(documentId)) }
        return report != null
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
