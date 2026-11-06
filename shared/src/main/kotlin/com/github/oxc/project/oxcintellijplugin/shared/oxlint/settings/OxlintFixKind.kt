package com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings

import kotlinx.serialization.Serializable

/** The names are stored in the settings file, so renaming one loses the value in existing projects. */
@Serializable
enum class OxlintFixKind(val serverValue: String) {
  SAFE_FIX("safe_fix"),
  SAFE_FIX_OR_SUGGESTION("safe_fix_or_suggestion"),
  DANGEROUS_FIX("dangerous_fix"),
  DANGEROUS_FIX_OR_SUGGESTION("dangerous_fix_or_suggestion"),
  NONE("none"),
  ALL("all"),
}
