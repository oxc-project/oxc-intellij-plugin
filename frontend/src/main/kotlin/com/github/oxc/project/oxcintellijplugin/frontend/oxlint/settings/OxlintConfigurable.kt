@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.settings.ToolConfigurable
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.DEFAULT_OXLINT_EXTENSIONS
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import fleet.rpc.client.durable

class OxlintConfigurable(project: Project) :
  ToolConfigurable<OxlintSettingsData>(project, "OxlintConfigurable", "oxlint", OxlintSettingsData()) {

  override suspend fun loadSettings(projectId: ProjectId?): OxlintSettingsData =
    durable { OxlintSettingsApi.getInstance().load(projectId) }

  override suspend fun applySettings(projectId: ProjectId?, settings: OxlintSettingsData) {
    durable { OxlintSettingsApi.getInstance().apply(projectId, settings) }
  }

  override fun Panel.createRows() {
    val enabled = modeRows(
      { model.configurationMode },
      { model = model.copy(configurationMode = it) },
      MutableProperty({ model.binaryPath }, { model = model.copy(binaryPath = it) }),
      MutableProperty({ model.configPath }, { model = model.copy(configPath = it) }),
    )

    row(OxcFrontendBundle.message("oxlint.settings.unused.disable.directives")) {
      comboBox(OxlintUnusedDisableDirectives.entries, textListCellRenderer {
        it?.let { value -> OxcFrontendBundle.message("oxlint.settings.unused.disable.directives.${value.serverValue}") }
      }).bindItem(
        { model.unusedDisableDirectives },
        { model = model.copy(unusedDisableDirectives = it ?: OxlintUnusedDisableDirectives.ALLOW) },
      )
    }.enabledIf(enabled)

    extensionsRow(enabled, DEFAULT_OXLINT_EXTENSIONS, { model.supportedExtensions }, {
      model = model.copy(supportedExtensions = it)
    })

    row {
      checkBox(OxcFrontendBundle.message("oxlint.settings.type.aware"))
        .bindSelected({ model.typeAware }, { model = model.copy(typeAware = it) })
    }.enabledIf(enabled)

    fixOnSaveLinkRow(enabled)

    row {
      checkBox(OxcFrontendBundle.message("oxlint.settings.disable.nested.config"))
        .bindSelected({ model.disableNestedConfig }, { model = model.copy(disableNestedConfig = it) })
    }.enabledIf(enabled)

    row(OxcFrontendBundle.message("oxlint.settings.fix.kind")) {
      comboBox(OxlintFixKind.entries, textListCellRenderer {
        it?.let { value -> OxcFrontendBundle.message("oxlint.settings.fix.kind.${value.serverValue}") }
      }).bindItem({ model.fixKind }, { model = model.copy(fixKind = it ?: OxlintFixKind.SAFE_FIX) })
    }.enabledIf(enabled)
  }
}
