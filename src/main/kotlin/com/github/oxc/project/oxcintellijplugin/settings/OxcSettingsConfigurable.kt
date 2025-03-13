package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.MyBundle
import com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerSupportProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class OxcSettingsConfigurable(private val project: Project) :
    BoundSearchableConfigurable(MyBundle.message("oxc.name"),
        "com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsConfigurable") {

    override fun createPanel(): DialogPanel {
        val settings = project.service<OxcSettingsComponent>()

        return panel {
            row {
                checkBox(MyBundle.message("oxc.settings.enabled")).bindSelected(settings::enable)
            }
            row(MyBundle.message("oxc.settings.languageServerPath")) {
                val textField = TextFieldWithBrowseButton()
                cell(textField).align(AlignX.FILL).applyToComponent {
                    val fileChooser = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withDescription(MyBundle.message("oxc.settings.selectPathToLanguageServer"))
                    addBrowseFolderListener(project, fileChooser)
                }.bindText({
                    return@bindText expandToSystemDependentPath(settings.binaryPath)
                }, {
                    settings.binaryPath = collapseToSystemIndependentPath(it)
                })
            }
        }
    }

    override fun apply() {
        super.apply()
        @Suppress("UnstableApiUsage") ApplicationManager.getApplication().invokeLater {
            project.service<LspServerManager>()
                .stopAndRestartIfNeeded(OxcLspServerSupportProvider::class.java)
        }
    }

    private fun collapseToSystemIndependentPath(path: String?): String {
        if (path.isNullOrBlank()) {
            return ""
        }

        val pathMacroManager = project.service<PathMacroManager>()
        return FileUtil.toSystemIndependentName(pathMacroManager.collapsePath(path))
    }

    private fun expandToSystemDependentPath(path: String?): String {
        if (path.isNullOrBlank()) {
            return ""
        }

        val pathMacroManager = project.service<PathMacroManager>()
        return FileUtil.toSystemDependentName(pathMacroManager.expandPath(path))
    }
}
