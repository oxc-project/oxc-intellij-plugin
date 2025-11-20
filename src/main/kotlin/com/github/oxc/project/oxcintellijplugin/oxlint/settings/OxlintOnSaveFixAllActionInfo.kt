package com.github.oxc.project.oxcintellijplugin.oxlint.settings


import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.intellij.ide.actionsOnSave.ActionOnSaveBackedByOwnConfigurable
import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext

class OxlintOnSaveFixAllActionInfo(actionOnSaveContext: ActionOnSaveContext) :
    ActionOnSaveBackedByOwnConfigurable<OxlintConfigurable>(actionOnSaveContext,
        OxlintConfigurable.CONFIGURABLE_ID,
        OxlintConfigurable::class.java) {

    override fun getActionOnSaveName() =
        OxlintBundle.message("oxlint.run.fix.all.on.save.checkbox.on.actions.on.save.page")

    override fun isApplicableAccordingToStoredState(): Boolean =
        OxlintSettings.getInstance(project).configurationMode != ConfigurationMode.DISABLED

    override fun isApplicableAccordingToUiState(configurable: OxlintConfigurable): Boolean =
        !configurable.disabledConfiguration.isSelected

    override fun isActionOnSaveEnabledAccordingToStoredState() = OxlintSettings.getInstance(project).fixAllOnSave

    override fun isActionOnSaveEnabledAccordingToUiState(configurable: OxlintConfigurable) =
        configurable.fixAllOnSaveCheckBox.isSelected

    override fun setActionOnSaveEnabled(configurable: OxlintConfigurable,
        enabled: Boolean) {
        configurable.fixAllOnSaveCheckBox.isSelected = enabled
    }

    override fun getCommentAccordingToUiState(configurable: OxlintConfigurable): ActionOnSaveComment? {
        return comment()
    }

    override fun getCommentAccordingToStoredState(): ActionOnSaveComment? {
        return comment()
    }

    override fun getActionLinks() = listOf(createGoToPageInSettingsLink(OxlintConfigurable.CONFIGURABLE_ID))

    private fun comment(): ActionOnSaveComment? {
        if (!isSaveActionApplicable) return ActionOnSaveComment.info(OxlintBundle.message("oxlint.on.save.comment.disabled"))

        return ActionOnSaveComment.info(OxlintBundle.message("oxlint.on.save.comment.fix.all"))
    }
}
