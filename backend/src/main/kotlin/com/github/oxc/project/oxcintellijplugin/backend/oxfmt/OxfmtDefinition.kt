package com.github.oxc.project.oxcintellijplugin.backend.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.lsp.LspDisabledCustomization
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.fix.OxfmtFixer
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspFormattingSupport
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolFixer
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolSettings
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingCustomizer

object OxfmtDefinition : ToolDefinition(
  tool = OxcTool.OXFMT,
  displayName = "Oxfmt",
  packageName = "oxfmt",
  messagePrefix = "oxfmt",
  configurableId = "OxfmtConfigurable",
  fixAllTimeoutRegistryKey = "oxc.fmt.fix.all.timeout.ms",
  vitePlusCommand = "fmt",
) {
  override val providerClass: Class<out ToolLspIntegrationProvider>
    get() = OxfmtLspIntegrationProvider::class.java

  override fun settings(project: Project): ToolSettings = OxfmtSettings.getInstance(project)

  /** The only keys that `oxfmt --lsp` reads. */
  override fun workspaceConfig(project: Project, configPath: String?): Map<String, Any?> = mapOf(
    "fmt.configPath" to configPath,
    "fmt.disableNestedConfig" to OxfmtSettings.getInstance(project).state.disableNestedConfig,
  )

  override fun lspCustomization(project: Project): LspCustomization = object : LspDisabledCustomization() {
    override val formattingCustomizer: LspFormattingCustomizer = OxfmtLspFormattingSupport(project)
  }

  override fun fixer(project: Project): ToolFixer = OxfmtFixer(project)
}
