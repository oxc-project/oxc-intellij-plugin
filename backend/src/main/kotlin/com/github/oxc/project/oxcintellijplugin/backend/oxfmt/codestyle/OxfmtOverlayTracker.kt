package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

/**
 * Every overlay depends on this, so that bumping it drops the platform's cached overlay of each file.
 * Notifying alone isn't enough: after Import, a file's cached overlay outlived the notification, and a
 * later config change never reached the file.
 */
@Service(Service.Level.PROJECT)
class OxfmtOverlayTracker : SimpleModificationTracker() {

  companion object {
    fun getInstance(project: Project): OxfmtOverlayTracker = project.service()
  }
}

/** Makes files ask the overlay again, which reads anything that changed. */
fun refreshOxfmtOverlay(project: Project) {
  OxfmtOverlayTracker.getInstance(project).incModificationCount()
  CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
}
