package com.github.oxc.project.oxcintellijplugin.oxfmt.actions

import com.github.oxc.project.oxcintellijplugin.extensions.findNearestOxfmtJsonConfigFile
import com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle.OxfmtCodeStyleImporter
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

class OxfmtImportCodeStyleAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val configFile = contextFile.findNearestOxfmtJsonConfigFile(project.guessProjectDir()) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(configFile) ?: return
        thisLogger().info("Importing Oxfmt code style from ${configFile.path}")

        OxfmtCodeStyleImporter(false).importConfigFile(psiFile)
        CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || contextFile == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = contextFile.findNearestOxfmtJsonConfigFile(
            project.guessProjectDir()) != null
    }
}
