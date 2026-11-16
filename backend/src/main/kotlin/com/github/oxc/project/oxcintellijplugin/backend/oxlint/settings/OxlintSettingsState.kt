package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XMap

private const val DISABLE_NESTED_CONFIG_KEY = "disable_nested_config"
private const val FIX_KIND_KEY = "fix_kind"

class OxlintSettingsState : BaseState() {

  var configurationMode by enum(ConfigurationMode.AUTOMATIC)
  var binaryPath by string()
  var configPath by string()
  var unusedDisableDirectives by enum(OxlintUnusedDisableDirectives.ALLOW)

  var supportedExtensions by list<String>()
  var typeAware by property(false)

  var fixAllOnSave by property(false)

  /**
   * Holds [disableNestedConfig] and [fixKind]. A missing key means the default.
   */
  @get:XMap
  var flags by map<String, String>()

  @get:Transient
  var disableNestedConfig: Boolean
    get() = flags[DISABLE_NESTED_CONFIG_KEY]?.toBoolean() ?: false
    set(value) = setFlag(DISABLE_NESTED_CONFIG_KEY, value.toString().takeIf { value })

  @get:Transient
  var fixKind: OxlintFixKind
    get() = storedFixKind() ?: OxlintFixKind.SAFE_FIX
    set(value) = setFlag(FIX_KIND_KEY, value.name.takeIf { value != OxlintFixKind.SAFE_FIX })

  /** Rewrites the fix kind in upper case, and drops a fix kind value that isn't known. */
  fun normalizeFlags() {
    val value = flags[FIX_KIND_KEY] ?: return
    val kind = storedFixKind()
    if (kind == null) {
      logger<OxlintSettingsState>().warn("Ignoring unknown Oxlint fix kind: $value")
    }
    setFlag(FIX_KIND_KEY, kind?.name)
  }

  /** Different casing has been used over time, so this normalizes it. */
  private fun storedFixKind(): OxlintFixKind? =
    flags[FIX_KIND_KEY]?.let { value -> OxlintFixKind.entries.find { it.name == value.uppercase() } }

  private fun setFlag(key: String, value: String?) {
    val updated = LinkedHashMap(flags)
    if (value == null) updated.remove(key) else updated[key] = value
    // Assignment counts as a tracked modification, so only do it when something actually happened.
    if (updated != flags) {
      flags = updated
    }
  }
}
