package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolPersistentSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.isOxfmtSupportedExtension
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
@State(
  name = "OxfmtSettings",
  storages = [Storage("OxfmtSettings.xml")],
  category = SettingsCategory.TOOLS,
)
class OxfmtSettings(project: Project) :
  ToolPersistentSettings<OxfmtSettingsState, OxfmtSettingsData>(project, OxcTool.OXFMT, OxfmtSettingsState()) {

  override val configurationMode: ConfigurationMode
    get() = state.configurationMode

  override val binaryPath: String?
    get() = state.binaryPath

  override val configPath: String?
    get() = state.configPath

  override var storedFixAllOnSave: Boolean
    get() = state.fixAllOnSave
    set(value) {
      state.fixAllOnSave = value
    }

  override fun isSupported(file: VirtualFile): Boolean =
    isOxfmtSupportedExtension(state.supportedExtensions, file.extension)

  override fun toData(): OxfmtSettingsData = OxfmtSettingsData(
    configurationMode = state.configurationMode,
    binaryPath = state.binaryPath.orEmpty(),
    configPath = state.configPath.orEmpty(),
    supportedExtensions = state.supportedExtensions.toList(),
    disableNestedConfig = state.disableNestedConfig,
    preferOxfmtCodeStyleSettings = state.preferOxfmtCodeStyleSettings,
  )

  override fun writeData(data: OxfmtSettingsData) {
    state.configurationMode = data.configurationMode
    state.binaryPath = data.binaryPath.ifBlank { null }
    state.configPath = data.configPath.ifBlank { null }
    state.supportedExtensions = data.supportedExtensions.toMutableList()
    state.disableNestedConfig = data.disableNestedConfig
    state.preferOxfmtCodeStyleSettings = data.preferOxfmtCodeStyleSettings
  }

  /** The code style preference happens in the plugin, so the servers don't need to know about it. */
  override fun affectsServers(previous: OxfmtSettingsData, current: OxfmtSettingsData): Boolean =
    previous.copy(preferOxfmtCodeStyleSettings = false) != current.copy(preferOxfmtCodeStyleSettings = false)

  companion object {
    fun getInstance(project: Project): OxfmtSettings = project.service()
  }
}
