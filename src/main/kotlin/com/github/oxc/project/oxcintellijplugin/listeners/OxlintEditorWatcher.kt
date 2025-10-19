package com.github.oxc.project.oxcintellijplugin.listeners

import com.github.oxc.project.oxcintellijplugin.services.OxlintServerService
import com.github.oxc.project.oxcintellijplugin.settings.OxlintSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class OxlintEditorWatcher : FileEditorManagerListener {

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
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
