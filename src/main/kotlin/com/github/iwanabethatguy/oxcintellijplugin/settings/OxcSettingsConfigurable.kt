package com.github.iwanabethatguy.oxcintellijplugin.settings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nullable
import javax.swing.JComponent
import javax.swing.JPanel


class OxcSettingsConfigurable: Configurable {
    private var settingsComponent: OxcSettingsComponent? = null


    // A default constructor with no arguments is required because this implementation
    // is registered in an applicationConfigurable EP
    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Oxc"
    }

    @Nullable
    override fun createComponent(): JPanel? {
        settingsComponent = OxcSettingsComponent()
        return settingsComponent!!.getPanel()
    }

    override fun isModified(): Boolean {
        val settings: OxcSettingsState = OxcSettingsState.instance
        val modified: Boolean = !settingsComponent!!.getEnable().equals(settings.enable)

        return modified
    }

    override fun apply() {
        val settings: OxcSettingsState = OxcSettingsState.instance
        settings.enable = settingsComponent!!.getEnable()
    }

    override fun reset() {
        val settings: OxcSettingsState = OxcSettingsState.instance
        settingsComponent!!.setEnable(settings.enable)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}