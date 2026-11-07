package com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.ui.components.ActionLink

private const val OXLINT_CONFIGURABLE_ID = "OxlintConfigurable"

/** On the backend, because a thin client's Actions on Save page is drawn from the backend's providers. */
class OxcOnSaveInfoProvider : ActionOnSaveInfoProvider() {

  override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
    listOf(OxlintFixAllOnSaveInfo(context))

  override fun getSearchableOptions(): Collection<String> =
    listOf(OxcBackendBundle.message("oxlint.fix.all.on.save.checkbox.on.actions.on.save.page"))
}

/** Edits the setting directly. The Oxlint page is frontend code, so it can't mirror that page's checkbox. */
class OxlintFixAllOnSaveInfo(context: ActionOnSaveContext) : ActionOnSaveInfo(context) {

  private val settings: OxlintSettings
    get() = OxlintSettings.getInstance(project)

  private var enabled = settings.isFixAllOnSaveEnabled

  override fun getActionOnSaveName(): String =
    OxcBackendBundle.message("oxlint.run.fix.all.on.save.checkbox.on.actions.on.save.page")

  override fun isSaveActionApplicable(): Boolean = settings.isEnabled

  override fun isActionOnSaveEnabled(): Boolean = enabled

  override fun setActionOnSaveEnabled(enabled: Boolean) {
    this.enabled = enabled
  }

  public override fun isModified(): Boolean = enabled != settings.isFixAllOnSaveEnabled

  public override fun apply() {
    settings.update(settings.toData().copy(fixAllOnSave = enabled))
  }

  override fun getComment(): ActionOnSaveComment = ActionOnSaveComment.info(
    OxcBackendBundle.message(
      if (isSaveActionApplicable) "oxlint.on.save.comment.fix.all" else "oxlint.on.save.comment.disabled"))

  /** The page isn't in a thin client's backend settings, so a link there would go nowhere. */
  override fun getActionLinks(): List<ActionLink> =
    if (getSettings()?.find(OXLINT_CONFIGURABLE_ID) == null) emptyList()
    else listOf(createGoToPageInSettingsLink(OXLINT_CONFIGURABLE_ID))
}
