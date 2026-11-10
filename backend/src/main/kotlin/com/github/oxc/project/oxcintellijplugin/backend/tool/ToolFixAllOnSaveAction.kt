package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixOnSaveFailure
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * The platform calls this off the UI thread and saves the document afterwards, so the changes are in
 * the file that gets written. A failure doesn't interrupt the save, so the file is saved without the
 * tool's changes. It's logged, and the frontend tells the user.
 */
abstract class ToolFixAllOnSaveAction(
  private val definition: ToolDefinition,
) : ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave() {

  override val presentableName: String
    get() = OxcBackendBundle.message("${definition.messagePrefix}.run.fix.all")

  override fun isEnabledForProject(project: Project): Boolean =
    definition.settings(project).isFixAllOnSaveEnabled

  override suspend fun updateDocument(project: Project, document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    if (!definition.settings(project).isSupported(file)) return

    val name = definition.displayName
    val start = System.nanoTime()
    try {
      OxcFixAll.getInstance(project).fixDocument(definition, file, document)
      thisLogger().debug { "$name fix on save took ${(System.nanoTime() - start) / 1_000_000} ms for ${file.path}" }
    }
    catch (_: TimeoutCancellationException) {
      thisLogger().warn("$name fix on save timed out for ${file.path}, skipping this file")
      report(project, file, OxcFixAllResult.TimedOut)
    }
    catch (exception: CancellationException) {
      throw exception
    }
    catch (exception: Exception) {
      thisLogger().warn("$name fix on save failed for ${file.path}", exception)
      report(project, file, OxcFixAllResult.Failed(exception.message ?: exception.toString()))
    }
  }

  private fun report(project: Project, file: VirtualFile, result: OxcFixAllResult) {
    OxcFixOnSaveFailures.getInstance(project).report(OxcFixOnSaveFailure(definition.tool, file.name, result))
  }
}
