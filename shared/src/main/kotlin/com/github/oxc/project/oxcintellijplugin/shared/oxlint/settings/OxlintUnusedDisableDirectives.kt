package com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings

import kotlinx.serialization.Serializable

/** The names are stored in the settings file, so renaming one loses the value in existing projects. */
@Serializable
enum class OxlintUnusedDisableDirectives(val serverValue: String) {
  ALLOW("allow"),
  WARN("warn"),
  DENY("deny"),
}
