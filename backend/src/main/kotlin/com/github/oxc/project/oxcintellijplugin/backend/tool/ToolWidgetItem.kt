package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem

/**
 * The tool's item in the language services status bar widget (FR-LSP-6), with the Oxc icon, opening the
 * tool's settings page, and a settings button beside it as v0.0.45 had.
 *
 * The platform's item opens a page by its class, and adds the settings button only when it has one, but the
 * pages are the frontend's, so the backend can't name the class. This opens the page by its id instead.
 */
internal class ToolWidgetItem(
  lspClient: LspClient,
  currentFile: VirtualFile?,
  private val definition: ToolDefinition,
) : LspClientWidgetItem(lspClient, currentFile, OxcIcons.OxcRound) {

  override fun createWidgetMainAction(): AnAction =
    DumbAwareAction.create(widgetActionText, OxcIcons.OxcRound, ::openSettings)

  /** After the platform's own buttons, where its settings button would be. */
  override fun createWidgetInlineActions(): List<AnAction> =
    super.createWidgetInlineActions() + DumbAwareAction.create(
      OxcBackendBundle.message("widget.open.settings.label"),
      AllIcons.General.Settings,
      ::openSettings,
    )

  private fun openSettings(event: AnActionEvent) {
    val project = event.project ?: return
    ShowSettingsUtil.getInstance().showSettingsDialog(
      project,
      { (it as? SearchableConfigurable)?.id == definition.configurableId },
      null,
    )
  }
}
