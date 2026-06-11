package com.github.oxc.project.oxcintellijplugin.actions

import com.github.oxc.project.oxcintellijplugin.OxcIcons
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class OxlintRestartServerAction : AnAction(), DumbAware {
    init {
        templatePresentation.icon = OxcIcons.OxcRound
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val service = OxlintServerService.getInstance(project)

        service.restartServer()
        service.notifyRestart()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
