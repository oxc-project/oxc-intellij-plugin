package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.MyBundle
import com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerSupportProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class OxcSettingsConfigurable(private val project: Project) :
    BoundSearchableConfigurable(MyBundle.message("oxc.name"),
        "com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsConfigurable") {

    override fun createPanel(): DialogPanel {
        val settings = project.service<OxcSettingsComponent>()

        return panel {
            row {
                checkBox("Enabled").bindSelected(settings::enable)
            }
        }
    }

    override fun apply() {
        super.apply()
        @Suppress("UnstableApiUsage")
        ApplicationManager.getApplication().invokeLater {
            project.service<LspServerManager>()
                .stopAndRestartIfNeeded(OxcLspServerSupportProvider::class.java)
        }
    }
}
