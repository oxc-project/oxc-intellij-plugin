package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

/**
 * Settings held only on the backend and written to a file in the project. [D] is the copy that the settings page edits.
 */
abstract class ToolPersistentSettings<S : BaseState, D : Any>(
  private val project: Project,
  private val tool: OxcTool,
  initialState: S,
) : SimplePersistentStateComponent<S>(initialState), ToolSettings {

  private var loaded = false

  override val modificationTracker: SimpleModificationTracker = SimpleModificationTracker()

  abstract fun toData(): D

  /** Changes the state in place, because that's how the store notices that it needs to be saved. */
  protected abstract fun writeData(data: D)

  /** Migrates an older on-disk format to the current one. */
  protected open fun migrate(state: S) {}

  protected open fun affectsServers(previous: D, current: D): Boolean = previous != current

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

  private fun notifyChanged(previous: D) {
    modificationTracker.incModificationCount()
    project.messageBus.syncPublisher(OxcSettingsListener.TOPIC)
      .settingsChanged(tool, affectsServers(previous, toData()))
  }
}
