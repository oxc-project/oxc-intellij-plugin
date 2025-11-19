package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxlintBundle
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class OxcOnSaveInfoProvider : ActionOnSaveInfoProvider() {
    override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
        listOf(OxcOnSaveFixAllActionInfo(context))

    override fun getSearchableOptions(): Collection<String> {
        return listOf(OxlintBundle.message("oxc.fix.all.on.save.checkbox.on.actions.on.save.page"))
    }
}
