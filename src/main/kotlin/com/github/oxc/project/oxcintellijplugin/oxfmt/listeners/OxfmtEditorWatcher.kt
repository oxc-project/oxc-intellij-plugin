package com.github.oxc.project.oxcintellijplugin.oxfmt.listeners

import com.github.oxc.project.oxcintellijplugin.OxcLspServerPool
import com.github.oxc.project.oxcintellijplugin.OxcLspTool
import com.github.oxc.project.oxcintellijplugin.oxfmt.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.vfs.VirtualFile

class OxfmtEditorWatcher : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        OxcLspServerPool.getInstance(event.manager.project).fileOpened(OxcLspTool.OXFMT, file)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        OxcLspServerPool.getInstance(project).fileClosed(file)
        if (source.allEditors.isEmpty()) {
            OxfmtServerService.getInstance(project).stopServer()
            return
        }

        val stillHasSupportedFileOpen = source.allEditors.any {
            OxfmtSettings.getInstance(project).fileSupported(it.file)
        }
        if (!stillHasSupportedFileOpen) {
            OxfmtServerService.getInstance(project).stopServer()
        }
    }

}
