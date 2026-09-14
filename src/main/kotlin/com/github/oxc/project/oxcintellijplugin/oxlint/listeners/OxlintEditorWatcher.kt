package com.github.oxc.project.oxcintellijplugin.oxlint.listeners

import com.github.oxc.project.oxcintellijplugin.OxcLspServerPool
import com.github.oxc.project.oxcintellijplugin.OxcLspTool
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.vfs.VirtualFile

class OxlintEditorWatcher : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        OxcLspServerPool.getInstance(event.manager.project).fileOpened(OxcLspTool.OXLINT, file)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        OxcLspServerPool.getInstance(project).fileClosed(file)
        if (source.allEditors.isEmpty()) {
            OxlintServerService.getInstance(project).stopServer()
            return
        }

        val stillHasSupportedFileOpen = source.allEditors.any {
            OxlintSettings.getInstance(project).fileSupported(it.file)
        }
        if (!stillHasSupportedFileOpen) {
            OxlintServerService.getInstance(project).stopServer()
        }
    }

}
