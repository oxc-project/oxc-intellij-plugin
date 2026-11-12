package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.openapi.components.BaseState

class OxfmtSettingsState : BaseState() {

  var configurationMode by enum(ConfigurationMode.AUTOMATIC)
  var binaryPath by string()
  var configPath by string()

  /** An empty list means the defaults, because a list with entries replaces them entirely. */
  var supportedExtensions by list<String>()
  var disableNestedConfig by property(false)
  var fixAllOnSave by property(false)

  /** The Vite+ CLI writes this as true. */
  var preferOxfmtCodeStyleSettings by property(false)
}
