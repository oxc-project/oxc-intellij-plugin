package com.github.oxc.project.oxcintellijplugin.settings


import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.intellij.ide.actionsOnSave.ActionOnSaveBackedByOwnConfigurable
import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext

class OxfmtOnSaveFixAllActionInfo(actionOnSaveContext: ActionOnSaveContext) :
    ActionOnSaveBackedByOwnConfigurable<OxfmtConfigurable>(actionOnSaveContext,
        OxfmtConfigurable.CONFIGURABLE_ID,
        OxfmtConfigurable::class.java) {

    override fun getActionOnSaveName() =
        OxcBundle.message("oxlint.run.fix.all.on.save.checkbox.on.actions.on.save.page")

    override fun isApplicableAccordingToStoredState(): Boolean =
        OxfmtSettings.getInstance(project).configurationMode != ConfigurationMode.DISABLED

    override fun isApplicableAccordingToUiState(configurable: OxfmtConfigurable): Boolean =
        !configurable.disabledConfiguration.isSelected

    override fun isActionOnSaveEnabledAccordingToStoredState() = OxfmtSettings.getInstance(project).fixAllOnSave

    override fun isActionOnSaveEnabledAccordingToUiState(configurable: OxfmtConfigurable) =
        configurable.fixAllOnSaveCheckBox.isSelected

    override fun setActionOnSaveEnabled(configurable: OxfmtConfigurable,
        enabled: Boolean) {
        configurable.fixAllOnSaveCheckBox.isSelected = enabled
    }

    override fun getCommentAccordingToUiState(configurable: OxfmtConfigurable): ActionOnSaveComment? {
        return comment()
    }

    override fun getCommentAccordingToStoredState(): ActionOnSaveComment? {
        return comment()
    }

    override fun getActionLinks() = listOf(createGoToPageInSettingsLink(OxfmtConfigurable.CONFIGURABLE_ID))

    private fun comment(): ActionOnSaveComment? {
        if (!isSaveActionApplicable) return ActionOnSaveComment.info(OxcBundle.message("oxlint.on.save.comment.disabled"))

        return ActionOnSaveComment.info(OxcBundle.message("oxlint.on.save.comment.fix.all"))
    }
}
