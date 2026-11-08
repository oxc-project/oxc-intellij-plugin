package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.project.Project

/**
 * Settings held only on the backend and written to a file in the project, which the Vite+ CLI and users
 * can edit too. [D] is the copy that the settings page edits.
 */
abstract class ToolPersistentSettings<S : BaseState, D : Any>(
  private val project: Project,
  private val tool: OxcTool,
  initialState: S,
) : SimplePersistentStateComponent<S>(initialState), ToolSettings {

  private var loaded = false

  protected abstract var storedFixAllOnSave: Boolean

  abstract fun toData(): D

  /** Changes the state in place, because that's how the store notices that it needs to be saved. */
  protected abstract fun writeData(data: D)

  protected open fun migrate(state: S) {}

  protected open fun affectsServers(previous: D, current: D): Boolean = previous != current

  override val isFixAllOnSaveEnabled: Boolean
    get() = isEnabled && storedFixAllOnSave

  /** Called when the file is read, including when something outside the IDE changes it. */
  override fun loadState(state: S) {
    migrate(state)
    val previous = toData()
    val changed = loaded && state != this.state
    super.loadState(state)
    loaded = true
    if (changed) notifyChanged(previous)
  }

  override fun noStateLoaded() {
    loaded = true
  }

  fun update(data: D) {
    val previous = toData()
    if (data == previous) return

    writeData(data)
    notifyChanged(previous)
  }

  /** Fix on save happens in the plugin, so the servers don't need to know that it changed. */
  override fun updateFixAllOnSave(enabled: Boolean) {
    if (storedFixAllOnSave == enabled) return

    storedFixAllOnSave = enabled
    publish(false)
  }

  private fun notifyChanged(previous: D) {
    publish(affectsServers(previous, toData()))
  }

  private fun publish(affectsServers: Boolean) {
    project.messageBus.syncPublisher(OxcSettingsListener.TOPIC).settingsChanged(tool, affectsServers)
  }
}
