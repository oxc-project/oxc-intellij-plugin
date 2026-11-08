package com.github.oxc.project.oxcintellijplugin.backend.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.lsp.LspDisabledCustomization
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix.OxlintFixer
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspDiagnosticsSupport
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolFixer
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolSettings
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspCodeActionsCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsCustomizer

object OxlintDefinition : ToolDefinition(
  tool = OxcTool.OXLINT,
  displayName = "Oxlint",
  packageName = "oxlint",
  messagePrefix = "oxlint",
  configurableId = "OxlintConfigurable",
  fixAllTimeoutRegistryKey = "oxc.lint.fix.all.timeout.ms",
) {
  override val providerClass: Class<out ToolLspIntegrationProvider>
    get() = OxlintLspIntegrationProvider::class.java

  override fun settings(project: Project): ToolSettings = OxlintSettings.getInstance(project)

  override fun workspaceConfig(project: Project, configPath: String?): Map<String, Any?> {
    val state = OxlintSettings.getInstance(project).state
    return mapOf(
      "configPath" to configPath,
      "disableNestedConfig" to state.disableNestedConfig,
      "fixKind" to state.fixKind.serverValue,
      "typeAware" to state.typeAware,
      "unusedDisableDirectives" to state.unusedDisableDirectives.serverValue,
    )
  }

  override fun lspCustomization(project: Project): LspCustomization = object : LspDisabledCustomization() {
    override val codeActionsCustomizer: LspCodeActionsCustomizer = LspCodeActionsSupport()
    override val diagnosticsCustomizer: LspDiagnosticsCustomizer = OxlintLspDiagnosticsSupport()
  }

  override fun fixer(project: Project): ToolFixer = OxlintFixer(project)
}
