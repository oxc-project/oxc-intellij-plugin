package com.github.oxc.project.oxcintellijplugin.backend.rpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * The plugin's lifetime in a project: disposed when the project closes or the plugin unloads. A parent for
 * what an RPC call keeps alive, such as a listener feeding a flow that a thin client collects, so that it
 * can't outlive the plugin's classes.
 */
@Service(Service.Level.PROJECT)
class OxcProjectDisposable : Disposable {

  override fun dispose() = Unit

  companion object {
    fun getInstance(project: Project): OxcProjectDisposable = project.service()
  }
}
