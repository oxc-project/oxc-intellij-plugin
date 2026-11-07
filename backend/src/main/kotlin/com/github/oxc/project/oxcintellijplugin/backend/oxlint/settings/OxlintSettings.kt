package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.isOxlintSupportedExtension
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** The file is in the project on the backend, and the Vite+ CLI and users can edit it too. */
@Service(Service.Level.PROJECT)
@State(
  name = "OxcSettings",
  storages = [Storage("OxcSettings.xml")],
  category = SettingsCategory.TOOLS,
)
class OxlintSettings(private val project: Project) :
  SimplePersistentStateComponent<OxlintSettingsState>(OxlintSettingsState()) {

  private var loaded = false

  val isEnabled: Boolean
    get() = state.configurationMode != ConfigurationMode.DISABLED

  val isFixAllOnSaveEnabled: Boolean
    get() = isEnabled && state.fixAllOnSave

  fun isSupported(file: VirtualFile): Boolean =
    isOxlintSupportedExtension(state.supportedExtensions, file.extension)

  /** Called when the file is read, including when something outside the IDE changes it. */
  override fun loadState(state: OxlintSettingsState) {
    state.migrateLegacyFlags()
    val previous = toData()
    val changed = loaded && state != this.state
    super.loadState(state)
    loaded = true
    if (changed) notifyChanged(previous)
  }

  override fun noStateLoaded() {
    loaded = true
  }

  fun toData(): OxlintSettingsData = OxlintSettingsData(
    configurationMode = state.configurationMode,
    binaryPath = state.binaryPath.orEmpty(),
    configPath = state.configPath.orEmpty(),
    unusedDisableDirectives = state.unusedDisableDirectives,
    supportedExtensions = state.supportedExtensions.toList(),
    typeAware = state.typeAware,
    disableNestedConfig = state.disableNestedConfig,
    fixKind = state.fixKind,
  )

  /** Changes the state in place, because that's how the store notices that it needs to be saved. */
  fun update(data: OxlintSettingsData) {
    val previous = toData()
    if (data == previous) return

    state.configurationMode = data.configurationMode
    state.binaryPath = data.binaryPath.ifBlank { null }
    state.configPath = data.configPath.ifBlank { null }
    state.unusedDisableDirectives = data.unusedDisableDirectives
    state.supportedExtensions = data.supportedExtensions.toMutableList()
    state.typeAware = data.typeAware
    state.disableNestedConfig = data.disableNestedConfig
    state.fixKind = data.fixKind
    notifyChanged(previous)
  }

  /** Fix on save happens in the plugin, so the servers don't need to know that it changed. */
  fun updateFixAllOnSave(enabled: Boolean) {
    if (state.fixAllOnSave == enabled) return

    state.fixAllOnSave = enabled
    project.messageBus.syncPublisher(OxlintSettingsListener.TOPIC).settingsChanged(false)
  }

  private fun notifyChanged(previous: OxlintSettingsData) {
    project.messageBus.syncPublisher(OxlintSettingsListener.TOPIC).settingsChanged(toData() != previous)
  }

  companion object {
    fun getInstance(project: Project): OxlintSettings = project.service()
  }
}
