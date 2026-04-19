package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.intellij.psi.codeStyle.CodeStyleSettings

interface OxfmtCodeStyleConfigurator {

    fun applySettings(settings: CodeStyleSettings, config: OxfmtConfig)
    fun isAlreadyApplied(settings: CodeStyleSettings, config: OxfmtConfig): Boolean
}
