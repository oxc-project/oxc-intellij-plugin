@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.settings.ToolConfigurable
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.DEFAULT_OXFMT_EXTENSIONS
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsData
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import fleet.rpc.client.durable

class OxfmtConfigurable(project: Project) :
  ToolConfigurable<OxfmtSettingsData>(project, "OxfmtConfigurable", "oxfmt", OxfmtSettingsData()) {

  override fun getHelpTopic(): String = "reference.settings.oxfmt"

  override suspend fun loadSettings(projectId: ProjectId?): OxfmtSettingsData =
    durable { OxfmtSettingsApi.getInstance().load(projectId) }

  override suspend fun applySettings(projectId: ProjectId?, settings: OxfmtSettingsData) {
    durable { OxfmtSettingsApi.getInstance().apply(projectId, settings) }
  }

  override fun Panel.createRows() {
    val enabled = modeRows(
      { model.configurationMode },
      { model = model.copy(configurationMode = it) },
      MutableProperty({ model.binaryPath }, { model = model.copy(binaryPath = it) }),
      MutableProperty({ model.configPath }, { model = model.copy(configPath = it) }),
    )

    extensionsRow(enabled, DEFAULT_OXFMT_EXTENSIONS, { model.supportedExtensions }, {
      model = model.copy(supportedExtensions = it)
    })

    fixOnSaveLinkRow(enabled)

    row {
      checkBox(OxcFrontendBundle.message("oxfmt.settings.disable.nested.config"))
        .bindSelected({ model.disableNestedConfig }, { model = model.copy(disableNestedConfig = it) })
    }.enabledIf(enabled)

    row {
      checkBox(OxcFrontendBundle.message("oxfmt.settings.prefer.code.style"))
        .bindSelected(
          { model.preferOxfmtCodeStyleSettings },
          { model = model.copy(preferOxfmtCodeStyleSettings = it) },
        )
    }.enabledIf(enabled)
  }
}
