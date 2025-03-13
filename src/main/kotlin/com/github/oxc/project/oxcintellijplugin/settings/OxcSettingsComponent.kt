package com.github.oxc.project.oxcintellijplugin.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "OxcSettings", storages = [Storage("OxcSettings.xml")],
    category = SettingsCategory.TOOLS)
class OxcSettingsComponent(private val project: Project) :
    SimplePersistentStateComponent<OxcSettingsState>(OxcSettingsState()) {

    var binaryPath
        get() = state.binaryPath
        set(value) {
            if (value.isNullOrBlank()) {
                state.binaryPath = null
                return
            }
            state.binaryPath = value
        }

    var enable
        get() = state.enable
        set(value) {
            state.enable = value
        }

    var runTrigger
        get() = state.runTrigger
        set(value) {
            state.runTrigger = value
        }
}
