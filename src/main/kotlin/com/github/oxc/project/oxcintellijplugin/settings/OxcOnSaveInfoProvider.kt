package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtOnSaveFixAllActionInfo
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintOnSaveFixAllActionInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class OxcOnSaveInfoProvider : ActionOnSaveInfoProvider() {

    override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
        listOf(OxlintOnSaveFixAllActionInfo(context), OxfmtOnSaveFixAllActionInfo(context))

    override fun getSearchableOptions(): Collection<String> {
        return listOf(
            OxlintBundle.message("oxlint.fix.all.on.save.checkbox.on.actions.on.save.page"),
            OxfmtBundle.message("oxfmt.fix.all.on.save.checkbox.on.actions.on.save.page"))
    }
}
