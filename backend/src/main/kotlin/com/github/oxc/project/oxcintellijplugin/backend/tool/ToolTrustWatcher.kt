package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.api.LspClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Starts the servers that open files need once their project is trusted. The platform starts none in an
 * untrusted project, and doesn't look at the files already open when trust comes.
 */
class ToolTrustWatcher : TrustedProjectsListener {

  override fun onProjectTrusted(project: Project) = startFor(project)

  // Settings | Trusted Locations names a folder and no project, so every open project trusted by now.
  override fun onProjectTrusted(locatedProject: TrustedProjectsLocator.LocatedProject) {
    val project = locatedProject.project
    if (project != null) startFor(project)
    else ProjectManager.getInstance().openProjects.filter(TrustedProjects::isProjectTrusted).forEach(::startFor)
  }

  private fun startFor(project: Project) {
    if (!project.isDisposed && !project.isDefault) project.service<ToolServerStarter>().startForOpenFiles()
  }
}

/** Starts each tool's servers for the files open in any client. */
@Service(Service.Level.PROJECT)
class ToolServerStarter(private val project: Project, private val scope: CoroutineScope) {

  // openFilesWithRemotes, because openFiles only lists the current client's files.
  @Suppress("SplitModeApiUsage", "UnstableApiUsage")
  fun startForOpenFiles() {
    scope.launch {
      if (!TrustedProjects.isProjectTrusted(project)) return@launch
      val files = readAction { FileEditorManager.getInstance(project).openFilesWithRemotes.toList() }
      val manager = LspClientManager.getInstance(project)
      for (definition in ToolDefinition.all) {
        val settings = definition.settings(project)
        for (file in files.filter(settings::isSupported)) {
          // The same as opening the file: a server already running for it is kept.
          val descriptor = readAction { definition.createDescriptor(project, file) } ?: continue
          manager.ensureClientStarted(definition.providerClass, descriptor)
        }
      }
    }
  }
}
