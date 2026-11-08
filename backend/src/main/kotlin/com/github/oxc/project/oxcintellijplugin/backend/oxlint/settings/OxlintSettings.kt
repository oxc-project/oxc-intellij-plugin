package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolPersistentSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.isOxlintSupportedExtension
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
@State(
  name = "OxcSettings",
  storages = [Storage("OxcSettings.xml")],
  category = SettingsCategory.TOOLS,
)
class OxlintSettings(project: Project) :
  ToolPersistentSettings<OxlintSettingsState, OxlintSettingsData>(project, OxcTool.OXLINT, OxlintSettingsState()) {

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
    isOxlintSupportedExtension(state.supportedExtensions, file.extension)

  override fun migrate(state: OxlintSettingsState) {
    state.migrateLegacyFlags()
  }

  override fun toData(): OxlintSettingsData = OxlintSettingsData(
    configurationMode = state.configurationMode,
    binaryPath = state.binaryPath.orEmpty(),
    configPath = state.configPath.orEmpty(),
    unusedDisableDirectives = state.unusedDisableDirectives,
    supportedExtensions = state.supportedExtensions.toList(),
    typeAware = state.typeAware,
    disableNestedConfig = state.disableNestedConfig,
    fixKind = state.fixKind,
  )

  override fun writeData(data: OxlintSettingsData) {
    state.configurationMode = data.configurationMode
    state.binaryPath = data.binaryPath.ifBlank { null }
    state.configPath = data.configPath.ifBlank { null }
    state.unusedDisableDirectives = data.unusedDisableDirectives
    state.supportedExtensions = data.supportedExtensions.toMutableList()
    state.typeAware = data.typeAware
    state.disableNestedConfig = data.disableNestedConfig
    state.fixKind = data.fixKind
  }

  companion object {
    fun getInstance(project: Project): OxlintSettings = project.service()
  }
}
