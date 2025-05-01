package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsState.Companion.DEFAULT_EXTENSION_LIST
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

@Service(Service.Level.PROJECT)
@State(name = "OxcSettings", storages = [Storage("OxcSettings.xml")],
    category = SettingsCategory.TOOLS)
class OxcSettings(private val project: Project) :
    SimplePersistentStateComponent<OxcSettingsState>(OxcSettingsState()) {

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

    var fixAllOnSave: Boolean
        get() = isEnabled() && state.fixAllOnSave
        set(value) {
            state.fixAllOnSave = value
        }

    var runTrigger
        get() = state.runTrigger
        set(value) {
            state.runTrigger = value
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
        @JvmStatic
        fun getInstance(project: Project): OxcSettings = project.service()
    }
}
