package com.github.oxc.project.oxcintellijplugin.listeners

import com.github.oxc.project.oxcintellijplugin.services.OxcServerService
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class EditorWatcher : FileEditorManagerListener {

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        if (source.allEditors.isEmpty()) {
            OxcServerService.getInstance(project).stopServer()
            return
        }

        val stillHasSupportedFileOpen = source.allEditors.any {
            OxcSettings.getInstance(project).fileSupported(it.file)
        }
        if (!stillHasSupportedFileOpen) {
            OxcServerService.getInstance(project).stopServer()
        }
    }

}
