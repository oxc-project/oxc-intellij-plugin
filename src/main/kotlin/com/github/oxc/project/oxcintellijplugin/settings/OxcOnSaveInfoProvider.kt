package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class OxcOnSaveInfoProvider : ActionOnSaveInfoProvider() {
    override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
        listOf(OxlintOnSaveFixAllActionInfo(context))

    override fun getSearchableOptions(): Collection<String> {
        return listOf(OxcBundle.message("oxlint.fix.all.on.save.checkbox.on.actions.on.save.page"))
    }
}
