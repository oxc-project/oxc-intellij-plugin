package com.github.oxc.project.oxcintellijplugin.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettingsState.Companion.DEFAULT_EXTENSION_LIST
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

@Service(Service.Level.PROJECT)
@State(name = "OxcSettings", storages = [Storage("OxcSettings.xml")],
    category = SettingsCategory.TOOLS)
class OxlintSettings(private val project: Project) :
    SimplePersistentStateComponent<OxlintSettingsState>(OxlintSettingsState()) {

    var configurationMode: ConfigurationMode
        get() = state.configurationMode
        set(value) {
            state.configurationMode = value
        }

    var supportedExtensions: MutableList<String>
        get() = state.supportedExtensions.takeIf { it.isNotEmpty() } ?: DEFAULT_EXTENSION_LIST.toMutableList()
        set(value) {
            state.supportedExtensions.clear()
            state.supportedExtensions.addAll(value)
        }

    var binaryPath: String
        get() = state.binaryPath ?: ""
        set(value) {
            state.binaryPath = value
        }

    var binaryParameters: MutableList<String>
        get() = state.binaryParameters
        set(value) {
            state.binaryParameters.clear()
            state.binaryParameters.addAll(value)
        }

    var configPath: String
        get() = state.configPath ?: ""
        set(value) {
            val file = File(value)
            if (file.isFile) {
                state.configPath = file.path
                return
            }
            state.configPath = value
        }

    var disableNestedConfig: Boolean
        get() {
            try {
                return flags.getOrDefault(DISABLE_NESTED_CONFIG_KEY, "false").toBoolean()
            } catch (exception: Exception) {
                thisLogger().warn("Invalid value found for $DISABLE_NESTED_CONFIG_KEY", exception)
                return false
            }
        }
        set(value) {
            flags[DISABLE_NESTED_CONFIG_KEY] = value.toString()
        }

    var fixAllOnSave: Boolean
        get() = isEnabled() && state.fixAllOnSave
        set(value) {
            state.fixAllOnSave = value
        }

    var fixKind: OxlintFixKind
        get() {
            try {
                return OxlintFixKind.valueOf(
                    flags.getOrDefault(FIX_KIND_KEY, OxlintFixKind.SAFE_FIX.name).uppercase())
            } catch (exception: Exception) {
                thisLogger().warn("Invalid value found for $FIX_KIND_KEY", exception)
                return OxlintFixKind.SAFE_FIX
            }
        }
        set(value) {
            flags[FIX_KIND_KEY] = value.name
        }

    /**
     * Only intended for legacy use. New config values should be in a separate property.
     */
    private var flags: MutableMap<String, String>
        get() = state.flags
        set(value) {
            state.flags = value
        }

    var runTrigger
        get() = state.runTrigger
        set(value) {
            state.runTrigger = value
        }

    var typeAware
        get() = state.typeAware
        set(value) {
            state.typeAware = value
        }

    var unusedDisableDirectivesSeverity
        get() = state.unusedDisableDirectives
        set(value) {
            state.unusedDisableDirectives = value
        }

    fun isEnabled(): Boolean {
        return configurationMode !== ConfigurationMode.DISABLED
    }

    fun fileSupported(file: VirtualFile): Boolean {
        val fileExtension = file.extension
        return if (fileExtension != null) {
            supportedExtensions.contains(".$fileExtension")
        } else {
            false
        }
    }

    companion object {

        const val DISABLE_NESTED_CONFIG_KEY = "disable_nested_config";
        const val FIX_KIND_KEY = "fix_kind";

        @JvmStatic
        fun getInstance(project: Project): OxlintSettings = project.service()
    }
}
