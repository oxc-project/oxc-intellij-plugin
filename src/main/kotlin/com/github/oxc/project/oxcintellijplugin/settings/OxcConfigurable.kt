package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.github.oxc.project.oxcintellijplugin.OxcPackage
import com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.services.OxcServerService
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.event.HyperlinkEvent

private const val HELP_TOPIC = "reference.settings.oxc"

class OxcConfigurable(private val project: Project) :
    BoundSearchableConfigurable(OxcBundle.message("oxc.name"), HELP_TOPIC, CONFIGURABLE_ID) {

    lateinit var fixAllOnSaveCheckBox: JCheckBox
    lateinit var disabledConfiguration: JRadioButton
    private lateinit var automaticConfiguration: JRadioButton
    private lateinit var manualConfiguration: JRadioButton
    private lateinit var extensionsField: JBTextField

    override fun createPanel(): DialogPanel {
        val settings = OxcSettings.getInstance(project)
        val server = OxcServerService.getInstance(project)

        return panel {
            buttonsGroup {
                row {
                    disabledConfiguration =
                        radioButton(JavaScriptBundle.message("settings.javascript.linters.autodetect.disabled",
                            displayName)).bindSelected(ConfigurationModeProperty(settings,
                            ConfigurationMode.DISABLED)).component
                }
                row {
                    automaticConfiguration =
                        radioButton(JavaScriptBundle.message("settings.javascript.linters.autodetect.configure.automatically",
                            displayName)).bindSelected(ConfigurationModeProperty(settings,
                            ConfigurationMode.AUTOMATIC)).component

                    val detectAutomaticallyHelpText =
                        JavaScriptBundle.message("settings.javascript.linters.autodetect.configure.automatically.help.text",
                            ApplicationNamesInfo.getInstance().fullProductName,
                            displayName,
                            "${OxcPackage.CONFIG_NAME}.json")

                    val helpLabel = ContextHelpLabel.create(detectAutomaticallyHelpText)
                    helpLabel.border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP)
                    cell(helpLabel)
                }
                row {
                    manualConfiguration =
                        radioButton(JavaScriptBundle.message("settings.javascript.linters.autodetect.configure.manually",
                            displayName)).bindSelected(ConfigurationModeProperty(settings,
                            ConfigurationMode.MANUAL)).component
                }
            }

            // *********************
            // Manual configuration row
            // *********************
            panel {
                row(OxcBundle.message("oxc.settings.languageServerPath")) {
                    textFieldWithBrowseButton(OxcBundle.message("oxc.settings.languageServerPath")) {
                        it.path
                    }.bindText(settings::binaryPath)
                }.visibleIf(manualConfiguration.selected)

                row(OxcBundle.message("oxc.config.path.label")) {
                    textFieldWithBrowseButton(
                        OxcBundle.message("oxc.config.path.label"),
                        project,
                    ) { it.path }.bindText(settings::configPath)
                }.visibleIf(manualConfiguration.selected)
            }

//            row(MyBundle.message("oxc.settings.oxlintRunTrigger")) {
//                comboBox(listOf(OxlintRunTrigger.ON_SAVE, OxlintRunTrigger.ON_TYPE)).bindItem({
//                    return@bindItem settings.runTrigger
//                }, {
//                    if (it != null) {
//                        settings.runTrigger = it
//                    }
//                })
//            }.enabledIf(!disabledConfiguration.selected)

            // *********************
            // Supported file extensions row
            // *********************
            row(OxcBundle.message("oxc.supported.extensions.label")) {
                extensionsField = textField().align(AlignX.FILL)
                    .bindText({ settings.supportedExtensions.joinToString(",") }, { value ->
                        settings.supportedExtensions =
                            value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                    }).validationOnInput { validateExtensions(it) }.applyToComponent {
                        font = font.deriveFont(font.size2D - 2f) // Reduce font size by 2 points
                    }.component

            }.enabledIf(!disabledConfiguration.selected)

            // Add help text with a "Reset" link below the field
            row {
                comment(OxcBundle.message("oxc.supported.extensions.comment") + " <a href=\"reset\">Reset to Defaults</a>").applyToComponent {
                    addHyperlinkListener { event ->
                        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "reset") {
                            extensionsField.text = OxcSettingsState.DEFAULT_EXTENSION_LIST.joinToString(",")
                        }
                    }
                }
            }.bottomGap(BottomGap.MEDIUM).enabledIf(!disabledConfiguration.selected)

            // *********************
            // Apply safe fixes on save row
            // *********************
            row {
                fixAllOnSaveCheckBox = checkBox(OxcBundle.message("oxc.run.fix.all.on.save.label")).bindSelected(
                    { settings.configurationMode != ConfigurationMode.DISABLED && settings.fixAllOnSave },
                    { settings.fixAllOnSave = it },
                ).component

                val link = ActionsOnSaveConfigurable.createGoToActionsOnSavePageLink()
                cell(link)
            }.enabledIf(!disabledConfiguration.selected)

            onApply {
                server.restartServer()
                server.notifyRestart()
            }
        }

    }

    override fun apply() {
        super.apply()
        @Suppress("UnstableApiUsage") ApplicationManager.getApplication().invokeLater {
            project.service<LspServerManager>().stopAndRestartIfNeeded(OxcLspServerSupportProvider::class.java)
        }
    }

    private fun validateExtensions(field: JBTextField): ValidationInfo? {
        val input = field.text
        val extensions = input.split(",").map { it.trim() }

        val invalidExtension = extensions.find { !it.matches(Regex("^\\.[a-zA-Z0-9]+$")) }
        return if (invalidExtension != null) {
            ValidationInfo("Invalid extension: $invalidExtension. Must start with '.' and contain only alphanumeric characters.",
                field)
        } else {
            null
        }
    }

    private class ConfigurationModeProperty(
        private val settings: OxcSettings,
        private val mode: ConfigurationMode,
    ) : MutableProperty<Boolean> {
        override fun get(): Boolean = settings.configurationMode == mode

        override fun set(value: Boolean) {
            if (value) {
                settings.configurationMode = mode
            }
        }
    }

    companion object {
        const val CONFIGURABLE_ID = "com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsConfigurable"
    }
}
