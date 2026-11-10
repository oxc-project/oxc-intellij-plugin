package com.github.oxc.project.oxcintellijplugin.shared

import kotlinx.serialization.Serializable

@Serializable
enum class ConfigurationMode {
  DISABLED,
  AUTOMATIC,
  MANUAL,
}
