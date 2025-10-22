package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxcBundle
import com.github.oxc.project.oxcintellijplugin.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.services.OxfmtServerService
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.event.HyperlinkEvent
import kotlinx.collections.immutable.toImmutableMap

private const val HELP_TOPIC = "reference.settings.oxc"

class OxfmtConfigurable(private val project: Project) :
    BoundSearchableConfigurable(OxcBundle.message("oxlint.name"), HELP_TOPIC, CONFIGURABLE_ID) {

    lateinit var fixAllOnSaveCheckBox: JCheckBox
    lateinit var disabledConfiguration: JRadioButton
    private lateinit var automaticConfiguration: JRadioButton
    private lateinit var manualConfiguration: JRadioButton
    private lateinit var extensionsField: JBTextField

    override fun createPanel(): DialogPanel {
        val settings = OxfmtSettings.getInstance(project)
        val server = OxfmtServerService.getInstance(project)

        return panel {
            // *********************
            // Configuration mode row
            // *********************
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
                            "${OxfmtPackage.CONFIG_NAME}.json")

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
            indent {
                panel {
                    row(OxcBundle.message("oxlint.settings.languageServerPath")) {
                        @Suppress("UnstableApiUsage")
                        textFieldWithBrowseButton(
                            OxcBundle.message("oxlint.settings.languageServerPath")) {
                            it.path
                        }.align(AlignX.FILL).bindText(settings::binaryPath)
                    }.visibleIf(manualConfiguration.selected)

                    row(OxcBundle.message("oxlint.config.path.label")) {
                        @Suppress("UnstableApiUsage")
                        textFieldWithBrowseButton(
                            OxcBundle.message("oxlint.config.path.label"),
                            project,
                        ) { it.path }.align(AlignX.FILL).bindText(settings::configPath)
                    }.visibleIf(manualConfiguration.selected)
                }
            }

            // *********************
            // Supported file extensions row
            // *********************
            row(OxcBundle.message("oxlint.supported.extensions.label")) {
                val parse = { it: String ->
                    it.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                }
                val join = { it: List<String> ->
                    it.joinToString(",")
                }

                val extensionsFieldCell = expandableTextField(parse, join).align(AlignX.FILL)
                    .bindText({ join(settings.supportedExtensions) }, { value ->
                        settings.supportedExtensions = parse(value)
                    }).validationOnInput {
                        validateExtensions(it)
                    }

                extensionsField = extensionsFieldCell.component

                // Add help text with a "Reset" link below the field
                extensionsFieldCell.comment(
                    OxcBundle.message("oxlint.supported.extensions.comment") +
                    " <a href=\"reset\">Reset to Defaults</a>"
                )
                extensionsFieldCell.comment!!.addHyperlinkListener { event ->
                    if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "reset") {
                        extensionsField.text = join(OxfmtSettingsState.DEFAULT_EXTENSION_LIST)
                    }
                }
            }.bottomGap(BottomGap.MEDIUM).enabledIf(!disabledConfiguration.selected)

            // *********************
            // Apply fixes on save row
            // *********************
            row {
                fixAllOnSaveCheckBox = checkBox(OxcBundle.message("oxlint.run.fix.all.on.save.label")).bindSelected(
                    { settings.configurationMode != ConfigurationMode.DISABLED && settings.fixAllOnSave },
                    { settings.fixAllOnSave = it },
                ).component

                val link = ActionsOnSaveConfigurable.createGoToActionsOnSavePageLink()
                cell(link)
            }.enabledIf(!disabledConfiguration.selected)

            // *********************
            // Language Server Flags row
            // *********************
            row("Language Server Flags") {}
            row {
                val table = TableView(
                    ListTableModel<Flag>(createFlagKeyColumn(), createFlagValueColumn()))

                cell(ToolbarDecorator.createDecorator(table).setAddAction {
                    table.listTableModel.addRow(Flag("", ""))
                }.setRemoveAction {
                    val selectedRow = table.selectedRow
                    if (selectedRow >= 0) {
                        table.listTableModel.removeRow(selectedRow)
                    }
                }.createPanel()).align(AlignX.FILL).onApply {
                    settings.flags = table.listTableModel.items.associate { it.key to it.value }
                        .toImmutableMap()
                }.onIsModified {
                    settings.flags != table.listTableModel.items.associate { it.key to it.value }
                }.onReset {
                    table.listTableModel.items = settings.flags.map { Flag(it.key, it.value) }
                }
            }

            onApply {
                if (project.isDefault) {
                    return@onApply
                }
                ApplicationManager.getApplication().invokeLater {
                    server.restartServer()
                }
            }
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
        private val settings: OxfmtSettings,
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
