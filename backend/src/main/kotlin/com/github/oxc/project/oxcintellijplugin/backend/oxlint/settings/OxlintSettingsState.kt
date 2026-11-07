package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger

private const val LEGACY_DISABLE_NESTED_CONFIG_KEY = "disable_nested_config"
private const val LEGACY_FIX_KIND_KEY = "fix_kind"

class OxlintSettingsState : BaseState() {

  var configurationMode by enum(ConfigurationMode.AUTOMATIC)
  var binaryPath by string()
  var configPath by string()
  var unusedDisableDirectives by enum(OxlintUnusedDisableDirectives.ALLOW)

  /** An empty list means the defaults, because a list with entries replaces them entirely. */
  var supportedExtensions by list<String>()
  var typeAware by property(false)

  var fixAllOnSave by property(false)
  var disableNestedConfig by property(false)
  var fixKind by enum(OxlintFixKind.SAFE_FIX)

  /** Only read, to move the values that older versions stored here into their own fields. */
  var flags by map<String, String>()

  fun migrateLegacyFlags() {
    val legacy = flags
    if (legacy.isEmpty()) return

    legacy[LEGACY_DISABLE_NESTED_CONFIG_KEY]?.let { disableNestedConfig = it.toBoolean() }
    legacy[LEGACY_FIX_KIND_KEY]?.let { value ->
      val fixKind = OxlintFixKind.entries.find { it.name == value.uppercase() }
      if (fixKind == null) {
        logger<OxlintSettingsState>().warn("Ignoring unknown Oxlint fix kind: $value")
      } else {
        this.fixKind = fixKind
      }
    }
    flags = mutableMapOf()
  }
}
