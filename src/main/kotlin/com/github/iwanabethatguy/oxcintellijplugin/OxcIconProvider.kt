package com.github.iwanabethatguy.oxcintellijplugin

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class OxcIconProvider : IconProvider(), DumbAware {

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element !is PsiFile) {
            return null
        }
        val file = element.viewProvider.virtualFile
        if (!file.isValid || file.isDirectory) {
            return null
        }
        if (Constants.CONFIG_FILES.contains(file.name)) {
            return OxcIcons.OxcRound
        }

        return null
    }
}
