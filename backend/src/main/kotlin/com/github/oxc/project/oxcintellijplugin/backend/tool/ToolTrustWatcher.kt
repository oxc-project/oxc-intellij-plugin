package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

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
