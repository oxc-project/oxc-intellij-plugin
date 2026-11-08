package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem

/**
 * The tool's item in the language services status bar widget (FR-LSP-6), with the Oxc icon, opening the
 * tool's settings page.
 *
 * The platform's item opens a page by its class, but the pages are the frontend's, so the backend can't name
 * the class. This opens the page by its id instead.
 */
internal class ToolWidgetItem(
  lspClient: LspClient,
  currentFile: VirtualFile?,
  private val definition: ToolDefinition,
) : LspClientWidgetItem(lspClient, currentFile, OxcIcons.OxcRound) {

  override fun createWidgetMainAction(): AnAction =
    DumbAwareAction.create(widgetActionText, OxcIcons.OxcRound) { event ->
      val project = event.project ?: return@create
      ShowSettingsUtil.getInstance().showSettingsDialog(
        project,
        { (it as? SearchableConfigurable)?.id == definition.configurableId },
        null,
      )
    }
}
