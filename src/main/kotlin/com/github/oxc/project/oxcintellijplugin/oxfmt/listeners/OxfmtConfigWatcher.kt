package com.github.oxc.project.oxcintellijplugin.oxfmt.listeners

import com.github.oxc.project.oxcintellijplugin.extensions.isOxfmtConfigFile
import com.github.oxc.project.oxcintellijplugin.extensions.isOxfmtJsonConfigFile
import com.github.oxc.project.oxcintellijplugin.extensions.isViteConfigFile
import com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle.OxfmtCodeStyleImporter
import com.github.oxc.project.oxcintellijplugin.oxfmt.services.OxfmtServerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

class OxfmtConfigWatcher : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val configChanged = events.any { event ->
            return@any event.file?.isOxfmtConfigFile() == true || event.file?.isViteConfigFile() == true
        }
        val jsonConfigEvents = events.filter { event ->
            return@filter event.file?.isOxfmtJsonConfigFile() == true
        }
        jsonConfigEvents.forEach { event ->
            val project = ProjectLocator.getInstance().guessProjectForFile(event.file!!) ?: return@forEach
            val psiFile = PsiManager.getInstance(project).findFile(event.file!!) ?: return@forEach
            OxfmtCodeStyleImporter(false).importConfigFile(psiFile)
        }

        if (configChanged) {
            val projectManager = ApplicationManager.getApplication().service<ProjectManager>()
            val openProjects = projectManager.openProjects
            openProjects.forEach { project ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        if (jsonConfigEvents.isNotEmpty()) {
                            CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
                        }
                        OxfmtServerService.getInstance(project).restartServer()
                    }
                }
            }
        }
    }
}
