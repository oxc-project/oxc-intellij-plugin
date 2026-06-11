package com.github.oxc.project.oxcintellijplugin.oxlint.actions

import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.oxlint.services.OxlintServerService
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

// The platform runs updateDocument off the EDT and saves the document after it returns,
// so the fix still lands before the file is written to disk, without blocking the UI.
class OxlintFixAllOnSaveAction : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {
    override val presentableName: String
        get() = OxlintBundle.message("oxlint.run.fix.all")

    override fun isEnabledForProject(project: Project): Boolean {
        return OxlintSettings.getInstance(project).fixAllOnSave
    }

    override suspend fun updateDocument(project: Project,
        document: Document) {
        val logger = thisLogger()
        val settings = OxlintSettings.getInstance(project)
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!settings.fileSupported(virtualFile)) {
            return
        }

        val start = System.nanoTime()
        try {
            OxlintServerService.getInstance(project).fixAll(virtualFile, document)
            logger.debug("Fix on save complete for ${virtualFile.path} in ${(System.nanoTime() - start) / 1_000_000}ms")
        } catch (e: TimeoutCancellationException) {
            // Per-file budget owned by OxlintServerService (registry key oxc.lint.fix.all.timeout.ms).
            logger.warn("Oxlint 'Fix All' on save timed out for ${virtualFile.path}; skipping this file")
        } catch (e: CancellationException) {
            // E.g. the user edited the file during save.
            throw e
        } catch (e: Exception) {
            logger.warn("Exception while applying oxlint fixes on save for ${virtualFile.path}", e)
        }
    }
}
