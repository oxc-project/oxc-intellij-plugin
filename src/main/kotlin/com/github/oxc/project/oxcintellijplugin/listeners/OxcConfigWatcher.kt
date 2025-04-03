package com.github.oxc.project.oxcintellijplugin.listeners

import com.github.oxc.project.oxcintellijplugin.extensions.isOxcConfigFile
import com.github.oxc.project.oxcintellijplugin.services.OxcServerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class OxcConfigWatcher : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val configChanged = events.any { event ->
            return@any event.file?.isOxcConfigFile() == true
        }

        if (configChanged) {
            val projectManager = ApplicationManager.getApplication().service<ProjectManager>()
            val openProjects = projectManager.openProjects
            openProjects.forEach { project ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        OxcServerService.Companion.getInstance(project).restartServer()
                    }
                }
            }
        }
    }
}