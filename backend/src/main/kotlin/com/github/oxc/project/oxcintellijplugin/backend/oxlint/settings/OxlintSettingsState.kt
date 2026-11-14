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

/**
 * Stored in the same layout as older versions of the plugin, so that a project file shared with one reads
 * the same in both.
 */
class OxlintSettingsState : BaseState() {

  var configurationMode by enum(ConfigurationMode.AUTOMATIC)
  var binaryPath by string()
  var configPath by string()
  var unusedDisableDirectives by enum(OxlintUnusedDisableDirectives.ALLOW)

  /** An empty list means the defaults, because a list with entries replaces them entirely. */
  var supportedExtensions by list<String>()
  var typeAware by property(false)

  var fixAllOnSave by property(false)

  /**
   * Holds [disableNestedConfig] and [fixKind], as `<flags><entry key="fix_kind" value="DANGEROUS_FIX" /></flags>`.
   * A missing key means the default. Other keys are left alone.
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

  /** Called when the file is read. Rewrites the fix kind in upper case, and drops one that isn't known. */
  fun normalizeFlags() {
    val value = flags[FIX_KIND_KEY] ?: return
    val kind = storedFixKind()
    if (kind == null) {
      logger<OxlintSettingsState>().warn("Ignoring unknown Oxlint fix kind: $value")
    }
    setFlag(FIX_KIND_KEY, kind?.name)
  }

  /** Older versions wrote the name in either case. */
  private fun storedFixKind(): OxlintFixKind? =
    flags[FIX_KIND_KEY]?.let { value -> OxlintFixKind.entries.find { it.name == value.uppercase() } }

  private fun setFlag(key: String, value: String?) {
    val updated = LinkedHashMap(flags)
    if (value == null) updated.remove(key) else updated[key] = value
    // Assigned only on a change, because assigning counts as a modification.
    if (updated != flags) {
      flags = updated
    }
  }
}
