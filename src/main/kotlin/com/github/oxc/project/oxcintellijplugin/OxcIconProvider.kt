package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.extensions.isOxfmtConfigFile
import com.github.oxc.project.oxcintellijplugin.extensions.isOxlintConfigFile
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class OxcIconProvider : IconProvider(), DumbAware {

    override fun getIcon(element: PsiElement,
        flags: Int): Icon? {
        if (element !is PsiFile) {
            return null
        }
        val file = element.viewProvider.virtualFile
        if (!file.isValid || file.isDirectory) {
            return null
        }
        val settings = OxlintSettings.getInstance(element.project)
        if (settings.state.configPath == file.path) {
            return OxcIcons.OxcRound
        }
        if (file.isOxlintConfigFile()) {
            return OxcIcons.OxcRound
        }
        if (file.isOxfmtConfigFile()) {
            return OxcIcons.OxcRound
        }

        return null
    }
}
