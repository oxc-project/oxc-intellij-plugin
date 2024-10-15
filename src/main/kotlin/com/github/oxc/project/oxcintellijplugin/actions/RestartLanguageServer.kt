package com.github.oxc.project.oxcintellijplugin.actions

import com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspServerManager

class RestartLanguageServer : AnAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.isEnabledAndVisible = project.service<OxcSettingsComponent>().state.enable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        @Suppress("UnstableApiUsage")
        ApplicationManager.getApplication().invokeLater {
            project.service<LspServerManager>()
                .stopAndRestartIfNeeded(OxcLspServerSupportProvider::class.java)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
