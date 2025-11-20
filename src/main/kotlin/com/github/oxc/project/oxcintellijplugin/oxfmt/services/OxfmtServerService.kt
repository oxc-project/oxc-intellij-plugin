package com.github.oxc.project.oxcintellijplugin.oxfmt.services

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.util.getLsp4jRange
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind

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

        val commandName = OxfmtBundle.message("oxfmt.run.quickfix")

        val codeActionParams = CodeActionParams(server.getDocumentIdentifier(file),
            getLsp4jRange(document, 0, document.textLength), CodeActionContext().apply {
                diagnostics = emptyList()
                only = listOf("source.fixAll.oxc")
                triggerKind = CodeActionTriggerKind.Automatic
            })

        val codeActionResults = server.sendRequest {
            it.textDocumentService.codeAction(codeActionParams)
        }

        WriteCommandAction.runWriteCommandAction(project, commandName, GROUP_ID, {
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
