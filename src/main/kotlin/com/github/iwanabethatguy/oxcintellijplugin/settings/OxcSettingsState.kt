package com.github.iwanabethatguy.oxcintellijplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.NotNull


/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "OxcSettings", storages = arrayOf(Storage("OxcSettings.xml")))
class OxcSettingsState : PersistentStateComponent<OxcSettingsState> {
    var run: Run = Run.OnSave
    var enable: Boolean = true

    companion object {
        val instance:OxcSettingsState by lazy {
            OxcSettingsState()
        }
    }

    override fun loadState(@NotNull state: OxcSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
    override fun getState(): OxcSettingsState {
        return this
    }

}



enum class Run {
    OnType, OnSave
}
