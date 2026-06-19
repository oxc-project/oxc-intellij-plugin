package com.github.oxc.project.oxcintellijplugin.oxlint.services

import com.github.oxc.project.oxcintellijplugin.NOTIFICATION_GROUP
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.platform.lsp.util.getLsp4jRange
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind

@Service(Service.Level.PROJECT)
class OxlintServerService(private val project: Project) {
    private val groupId = "Oxc"

    companion object {
        fun getInstance(project: Project): OxlintServerService = project.getService(OxlintServerService::class.java)

        private const val FIX_ALL_TIMEOUT_REGISTRY_KEY = "oxc.lint.fix.all.timeout.ms"
        private const val DEFAULT_FIX_ALL_TIMEOUT_MS = 30_000

        // The platform's suspending sendRequest has no timeout of its own, so this bounds how
        // long fixAll may delay a save or block the manual action for a single file.
        private fun fixAllTimeoutMs(): Long {
            val value = Registry.intValue(FIX_ALL_TIMEOUT_REGISTRY_KEY, DEFAULT_FIX_ALL_TIMEOUT_MS)
            return (if (value > 0) value else DEFAULT_FIX_ALL_TIMEOUT_MS).toLong()
        }
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

        val fixKind = OxlintSettings.getInstance(project).fixKind
        if (fixKind == OxlintFixKind.NONE) {
            return false
        }
        val codeActionKinds = when {
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

        // Bound the code-action round-trip with a per-file budget so a single slow file is
        // skipped rather than starving the rest of a Save All batch.
        val codeActionResults = withTimeout(fixAllTimeoutMs()) {
            server.sendRequest { it.textDocumentService.codeAction(codeActionParams) }
        }
        val actions = codeActionResults.orEmpty()
            .filter { it.isRight && it.right.isPreferred }
            .map { LspIntentionAction(server, it.right) }
            .filter { ReadAction.compute<Boolean, Throwable> { it.isAvailable() } }

        if (actions.isEmpty()) {
            return false
        }

        WriteCommandAction.runWriteCommandAction(project, OxlintBundle.message("oxlint.run.quickfix"), groupId, {
            invokeActions(actions, file)
        })

        return true
    }

    private fun invokeActions(actions: List<LspIntentionAction>, file: VirtualFile) {
        val application = ApplicationManager.getApplication()
        val invoke = { actions.forEach { it.invoke(file) } }

        if (application.isDispatchThread) {
            invoke()
        } else {
            application.invokeAndWait(invoke, ModalityState.defaultModalityState())
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
