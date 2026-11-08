package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.ide.actionsOnSave.ActionOnSaveComment
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.openapi.util.Key
import com.intellij.ui.components.ActionLink

/** On the backend, because a thin client's Actions on Save page is drawn from the backend's providers. */
class OxcOnSaveInfoProvider : ActionOnSaveInfoProvider() {

  override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
    ToolDefinition.all.map { ToolFixAllOnSaveInfo(context, it) }

  override fun getSearchableOptions(): Collection<String> =
    ToolDefinition.all.map { OxcBackendBundle.message("${it.messagePrefix}.fix.all.on.save.checkbox.on.actions.on.save.page") }
}

private val PENDING_FIX_ALL_ON_SAVE: Map<OxcTool, Key<Boolean>> =
  OxcTool.entries.associateWith { Key.create("oxc.${it.name.lowercase()}.pending.fix.all.on.save") }

/**
 * The only place to edit fix on save. An unapplied edit is kept in the context, because the platform
 * makes new rows each time the page is shown.
 */
class ToolFixAllOnSaveInfo(context: ActionOnSaveContext, private val definition: ToolDefinition) : ActionOnSaveInfo(context) {

  private val settings: ToolSettings
    get() = definition.settings(project)

  private val pendingKey: Key<Boolean> = PENDING_FIX_ALL_ON_SAVE.getValue(definition.tool)

  private var pending: Boolean?
    get() = context.getUserData(pendingKey)
    set(value) = context.putUserData(pendingKey, value)

  override fun getActionOnSaveName(): String =
    OxcBackendBundle.message("${definition.messagePrefix}.run.fix.all.on.save.checkbox.on.actions.on.save.page")

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
      "${definition.messagePrefix}.on.save.comment." + if (isSaveActionApplicable) "fix.all" else "disabled"))

  /** The page isn't in a thin client's backend settings, so a link there would go nowhere. */
  override fun getActionLinks(): List<ActionLink> =
    if (getSettings().find(definition.configurableId) == null) emptyList()
    else listOf(createGoToPageInSettingsLink(definition.configurableId))
}
