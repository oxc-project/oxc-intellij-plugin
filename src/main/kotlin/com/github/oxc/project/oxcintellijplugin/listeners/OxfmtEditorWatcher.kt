package com.github.oxc.project.oxcintellijplugin.listeners

import com.github.oxc.project.oxcintellijplugin.services.OxfmtServerService
import com.github.oxc.project.oxcintellijplugin.settings.OxfmtSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class OxfmtEditorWatcher : FileEditorManagerListener {

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
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
