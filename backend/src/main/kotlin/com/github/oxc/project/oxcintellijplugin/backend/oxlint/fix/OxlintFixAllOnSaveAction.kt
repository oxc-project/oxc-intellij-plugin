package com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * The platform calls this off the UI thread and saves the document afterwards, so the fixes are in the
 * file that gets written. A failure doesn't interrupt the save or show anything, only logs.
 */
class OxlintFixAllOnSaveAction : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {

  override val presentableName: String
    get() = OxcBackendBundle.message("oxlint.run.fix.all")

  override fun isEnabledForProject(project: Project): Boolean =
    OxlintSettings.getInstance(project).isFixAllOnSaveEnabled

  override suspend fun updateDocument(project: Project, document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    if (!OxlintSettings.getInstance(project).isSupported(file)) return

    val start = System.nanoTime()
    try {
      OxlintFixAll.getInstance(project).fixDocument(file, document)
      thisLogger().debug { "Oxlint fix on save took ${(System.nanoTime() - start) / 1_000_000} ms for ${file.path}" }
    }
    catch (_: TimeoutCancellationException) {
      thisLogger().warn("Oxlint fix on save timed out for ${file.path}, skipping this file")
    }
    catch (exception: CancellationException) {
      throw exception
    }
    catch (exception: Exception) {
      thisLogger().warn("Oxlint fix on save failed for ${file.path}", exception)
    }
  }
}
