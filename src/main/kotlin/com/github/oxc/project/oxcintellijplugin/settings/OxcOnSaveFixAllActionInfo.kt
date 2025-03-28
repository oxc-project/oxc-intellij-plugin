package com.github.oxc.project.oxcintellijplugin.settings


import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.intellij.ide.actionsOnSave.ActionOnSaveBackedByOwnConfigurable
import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext

class OxcOnSaveFixAllActionInfo(actionOnSaveContext: ActionOnSaveContext) :
    ActionOnSaveBackedByOwnConfigurable<OxcConfigurable>(actionOnSaveContext,
        OxcConfigurable.CONFIGURABLE_ID,
        OxcConfigurable::class.java) {

    override fun getActionOnSaveName() =
        OxcBundle.message("oxc.run.fix.all.on.save.checkbox.on.actions.on.save.page")

    override fun isApplicableAccordingToStoredState(): Boolean =
        OxcSettings.getInstance(project).configurationMode != ConfigurationMode.DISABLED

    override fun isApplicableAccordingToUiState(configurable: OxcConfigurable): Boolean =
        !configurable.disabledConfiguration.isSelected

    override fun isActionOnSaveEnabledAccordingToStoredState() = OxcSettings.getInstance(project).fixAllOnSave

    override fun isActionOnSaveEnabledAccordingToUiState(configurable: OxcConfigurable) =
        configurable.fixAllOnSaveCheckBox.isSelected

    override fun setActionOnSaveEnabled(configurable: OxcConfigurable,
        enabled: Boolean) {
        configurable.fixAllOnSaveCheckBox.isSelected = enabled
    }

    override fun getCommentAccordingToUiState(configurable: OxcConfigurable): ActionOnSaveComment? {
        return comment()
    }

    override fun getCommentAccordingToStoredState(): ActionOnSaveComment? {
        return comment()
    }

    override fun getActionLinks() = listOf(createGoToPageInSettingsLink(OxcConfigurable.CONFIGURABLE_ID))

    private fun comment(): ActionOnSaveComment? {
        if (!isSaveActionApplicable) return ActionOnSaveComment.info(OxcBundle.message("oxc.on.save.comment.disabled"))

        return ActionOnSaveComment.info(OxcBundle.message("oxc.on.save.comment.fix.all"))
    }
}
