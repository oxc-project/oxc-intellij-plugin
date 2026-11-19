package com.github.oxc.project.oxcintellijplugin.backend.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * The plugin's lifetime in a project: disposed when the project closes or the plugin unloads.
 */
@Service(Service.Level.PROJECT)
class OxcProjectDisposable : Disposable {

  override fun dispose() = Unit

  companion object {

    fun getInstance(project: Project): OxcProjectDisposable = project.service()
  }
}
