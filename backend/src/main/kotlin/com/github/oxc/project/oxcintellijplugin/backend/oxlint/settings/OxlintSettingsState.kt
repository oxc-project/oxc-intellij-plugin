package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.xmlb.annotations.XMap

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

  /**
   * Where older versions of the plugin keep [disableNestedConfig] and [fixKind], as
   * `<flags><entry key="fix_kind" value="DANGEROUS_FIX" /></flags>`. It's kept in step with those
   * fields, so that a project file shared with an older version reads the same in both. A missing
   * key means the default, in both versions. Other keys are left alone.
   */
  @get:XMap
  var flags by map<String, String>()

  /**
   * Called when the file is read. A typed field that the file leaves at its default takes the
   * value from [flags]; one that the file sets wins over [flags].
   */
  fun mergeLegacyFlags() {
    val legacy = flags
    if (!disableNestedConfig) {
      legacy[LEGACY_DISABLE_NESTED_CONFIG_KEY]?.let { disableNestedConfig = it.toBoolean() }
    }
    if (fixKind == OxlintFixKind.SAFE_FIX) {
      legacy[LEGACY_FIX_KIND_KEY]?.let { value ->
        val legacyFixKind = OxlintFixKind.entries.find { it.name == value.uppercase() }
        if (legacyFixKind == null) {
          logger<OxlintSettingsState>().warn("Ignoring unknown Oxlint fix kind: $value")
        }
        else {
          fixKind = legacyFixKind
        }
      }
    }
    syncLegacyFlags()
  }

  /** Writes [disableNestedConfig] and [fixKind] into [flags], in the form older versions read. */
  fun syncLegacyFlags() {
    val synced = LinkedHashMap(flags)
    if (disableNestedConfig) {
      synced[LEGACY_DISABLE_NESTED_CONFIG_KEY] = true.toString()
    }
    else {
      synced.remove(LEGACY_DISABLE_NESTED_CONFIG_KEY)
    }
    if (fixKind != OxlintFixKind.SAFE_FIX) {
      synced[LEGACY_FIX_KIND_KEY] = fixKind.name
    }
    else {
      synced.remove(LEGACY_FIX_KIND_KEY)
    }
    // Assigned only on a change, because assigning counts as a modification.
    if (synced != flags) {
      flags = synced
    }
  }
}
