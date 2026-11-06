package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.DEFAULT_OXLINT_EXTENSIONS
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
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

  fun isSupported(file: VirtualFile): Boolean {
    val extensions = state.supportedExtensions.ifEmpty { DEFAULT_OXLINT_EXTENSIONS }
    return file.extension?.let { ".$it" in extensions } == true
  }

  /** Called when the file is read, including when something outside the IDE changes it. */
  override fun loadState(state: OxlintSettingsState) {
    state.migrateLegacyFlags()
    val changed = loaded && state != this.state
    super.loadState(state)
    loaded = true
    if (changed) notifyChanged()
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
    if (data == toData()) return

    state.configurationMode = data.configurationMode
    state.binaryPath = data.binaryPath.ifBlank { null }
    state.configPath = data.configPath.ifBlank { null }
    state.unusedDisableDirectives = data.unusedDisableDirectives
    state.supportedExtensions = data.supportedExtensions.toMutableList()
    state.typeAware = data.typeAware
    state.disableNestedConfig = data.disableNestedConfig
    state.fixKind = data.fixKind
    notifyChanged()
  }

  private fun notifyChanged() {
    project.messageBus.syncPublisher(OxlintSettingsListener.TOPIC).settingsChanged()
  }

  companion object {
    fun getInstance(project: Project): OxlintSettings = project.service()
  }
}
