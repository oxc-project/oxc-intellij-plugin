package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxlintRunTrigger
import com.intellij.openapi.components.BaseState
import com.intellij.util.xml.Attribute

class OxcSettingsState : BaseState() {

    @get:Attribute("binaryPath")
    var binaryPath by string()

    @get:Attribute("enable")
    var enable by property(true)

    @get:Attribute("runTrigger")
    var runTrigger by enum(OxlintRunTrigger.ON_TYPE)
}
