package com.github.iwanabethatguy.oxcintellijplugin.actions

import com.github.iwanabethatguy.oxcintellijplugin.lsp.OxcLspServerSupportProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspServerManager

@Suppress("UnstableApiUsage")
class RestartLanguageServer : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ApplicationManager.getApplication().invokeLater {
            project.service<LspServerManager>()
                .stopAndRestartIfNeeded(OxcLspServerSupportProvider::class.java)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
