package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.Constants
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.lsp.api.LspServerManager

class OxlintConfigWatcher : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val configChanged = events.any { event ->
            val fileName = event.path.substringAfterLast("/")

            return@any Constants.CONFIG_FILES.contains(fileName)
        }

        if (configChanged) {
            val projectManager = ApplicationManager.getApplication().service<ProjectManager>()
            val openProjects = projectManager.openProjects
            openProjects.forEach { project ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        @Suppress("UnstableApiUsage") project.service<LspServerManager>()
                            .stopAndRestartIfNeeded(OxcLspServerSupportProvider::class.java)
                    }
                }
            }
        }
    }
}
