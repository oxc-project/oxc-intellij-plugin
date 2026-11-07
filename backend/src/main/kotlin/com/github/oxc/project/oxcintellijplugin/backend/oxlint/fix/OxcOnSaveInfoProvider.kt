package com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.openapi.util.Key
import com.intellij.ui.components.ActionLink

private const val OXLINT_CONFIGURABLE_ID = "OxlintConfigurable"

/** On the backend, because a thin client's Actions on Save page is drawn from the backend's providers. */
class OxcOnSaveInfoProvider : ActionOnSaveInfoProvider() {

  override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
    listOf(OxlintFixAllOnSaveInfo(context))

  override fun getSearchableOptions(): Collection<String> =
    listOf(OxcBackendBundle.message("oxlint.fix.all.on.save.checkbox.on.actions.on.save.page"))
}

private val PENDING_FIX_ALL_ON_SAVE = Key.create<Boolean>("oxc.oxlint.pending.fix.all.on.save")

/**
 * The only place to edit fix on save. An unapplied edit is kept in the context, because the platform
 * makes new rows each time the page is shown.
 */
class OxlintFixAllOnSaveInfo(context: ActionOnSaveContext) : ActionOnSaveInfo(context) {

  private val settings: OxlintSettings
    get() = OxlintSettings.getInstance(project)

  private var pending: Boolean?
    get() = context.getUserData(PENDING_FIX_ALL_ON_SAVE)
    set(value) = context.putUserData(PENDING_FIX_ALL_ON_SAVE, value)

  override fun getActionOnSaveName(): String =
    OxcBackendBundle.message("oxlint.run.fix.all.on.save.checkbox.on.actions.on.save.page")

  override fun isSaveActionApplicable(): Boolean = settings.isEnabled

  override fun isActionOnSaveEnabled(): Boolean = pending ?: settings.isFixAllOnSaveEnabled

  override fun setActionOnSaveEnabled(enabled: Boolean) {
    pending = enabled
  }

  public override fun isModified(): Boolean = pending.let { it != null && it != settings.isFixAllOnSaveEnabled }

  public override fun apply() {
    pending?.let(settings::updateFixAllOnSave)
    pending = null
  }

  override fun getComment(): ActionOnSaveComment = ActionOnSaveComment.info(
    OxcBackendBundle.message(
      if (isSaveActionApplicable) "oxlint.on.save.comment.fix.all" else "oxlint.on.save.comment.disabled"))

  /** The page isn't in a thin client's backend settings, so a link there would go nowhere. */
  override fun getActionLinks(): List<ActionLink> =
    if (getSettings().find(OXLINT_CONFIGURABLE_ID) == null) emptyList()
    else listOf(createGoToPageInSettingsLink(OXLINT_CONFIGURABLE_ID))
}
