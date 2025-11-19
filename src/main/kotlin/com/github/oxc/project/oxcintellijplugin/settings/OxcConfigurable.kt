package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.OxcPackage
import com.github.oxc.project.oxcintellijplugin.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.OxlintRunTrigger
import com.github.oxc.project.oxcintellijplugin.OxlintUnusedDisableDirectivesSeverity
import com.github.oxc.project.oxcintellijplugin.services.OxcServerService
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox
import javax.swing.JRadioButton
import javax.swing.event.HyperlinkEvent

private const val HELP_TOPIC = "reference.settings.oxc"

class OxcConfigurable(private val project: Project) :
    BoundSearchableConfigurable(OxlintBundle.message("oxc.name"), HELP_TOPIC, CONFIGURABLE_ID) {

    lateinit var fixAllOnSaveCheckBox: JCheckBox
    lateinit var disabledConfiguration: JRadioButton
    private lateinit var automaticConfiguration: JRadioButton
    private lateinit var manualConfiguration: JRadioButton
    private lateinit var extensionsField: JBTextField

    override fun createPanel(): DialogPanel {
        val settings = OxcSettings.getInstance(project)
        val server = OxcServerService.getInstance(project)

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
            indent {
                panel {
                    row(OxlintBundle.message("oxc.settings.languageServerPath")) {
                        @Suppress("UnstableApiUsage")
                        textFieldWithBrowseButton(
                            OxlintBundle.message("oxc.settings.languageServerPath")) {
                            it.path
                        }.align(AlignX.FILL).bindText(settings::binaryPath)
                    }.visibleIf(manualConfiguration.selected)

                    row(OxlintBundle.message("oxc.config.path.label")) {
                        @Suppress("UnstableApiUsage")
                        textFieldWithBrowseButton(
                            OxlintBundle.message("oxc.config.path.label"),
                            project,
                        ) { it.path }.align(AlignX.FILL).bindText(settings::configPath)
                    }.visibleIf(manualConfiguration.selected)

                    row {
                        checkBox(OxlintBundle.message("oxc.manual.add.lsp.argument")).bindSelected(
                            { settings.binaryParameters.contains("--lsp") },
                            { isChecked ->
                                if (isChecked) {
                                    if (!settings.binaryParameters.contains("--lsp")) {
                                        settings.binaryParameters.add("--lsp")
                                    }
                                } else {
                                    settings.binaryParameters.removeIf({ it == "--lsp" })
                                }
                            },
                        )

                        val helpLabel = ContextHelpLabel.create(OxlintBundle.message("oxc.manual.add.lsp.argument.help"))
                        helpLabel.border = JBUI.Borders.emptyLeft(UIUtil.DEFAULT_HGAP)
                        cell(helpLabel)
                    }.visibleIf(manualConfiguration.selected)
                }
            }

            // *********************
            // Oxlint execution trigger row
            // *********************
            row(OxlintBundle.message("oxc.settings.oxlintRunTrigger")) {
                // TODO: Probably a better way to do this map of enum to presentation text.
                val options = mapOf(
                    OxlintRunTrigger.ON_SAVE to "On Save",
                    OxlintRunTrigger.ON_TYPE to "On Type",
                )
                val reverseOptions = options.entries.associateBy({ it.value }, { it.key })

                comboBox(options.values).bindItem({
                    return@bindItem options[settings.runTrigger]
                }, {
                    if (it != null) {
                        settings.runTrigger = reverseOptions[it]!!
                    }
                })
            }.enabledIf(!disabledConfiguration.selected)

            // *********************
            // Oxlint unused disable directives row
            // *********************
            row(OxlintBundle.message("oxc.settings.unusedDisableDirectives")) {
                // TODO: Probably a better way to do this map of enum to presentation text.
                val options = mapOf(
                    OxlintUnusedDisableDirectivesSeverity.ALLOW to "Allow",
                    OxlintUnusedDisableDirectivesSeverity.WARN to "Warn",
                    OxlintUnusedDisableDirectivesSeverity.DENY to "Deny",
                )
                val reverseOptions = options.entries.associateBy({ it.value }, { it.key })

                comboBox(options.values).bindItem({
                    return@bindItem options[settings.unusedDisableDirectivesSeverity]
                }, {
                    if (it != null) {
                        settings.unusedDisableDirectivesSeverity = reverseOptions[it]!!
                    }
                })
            }.enabledIf(!disabledConfiguration.selected)

            // *********************
            // Supported file extensions row
            // *********************
            row(OxlintBundle.message("oxc.supported.extensions.label")) {
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
                    OxlintBundle.message("oxc.supported.extensions.comment") +
                    " <a href=\"reset\">Reset to Defaults</a>"
                )
                extensionsFieldCell.comment!!.addHyperlinkListener { event ->
                    if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "reset") {
                        extensionsField.text = join(OxcSettingsState.DEFAULT_EXTENSION_LIST)
                    }
                }
            }.bottomGap(BottomGap.MEDIUM).enabledIf(!disabledConfiguration.selected)

            // *********************
            // Type aware row
            // *********************
            row {
                checkBox(OxlintBundle.message("oxc.type.aware.label")).bindSelected(
                    { settings.typeAware },
                    { settings.typeAware = it },
                )
            }.enabledIf(!disabledConfiguration.selected)

            // *********************
            // Apply fixes on save row
            // *********************
            row {
                fixAllOnSaveCheckBox = checkBox(OxlintBundle.message("oxc.run.fix.all.on.save.label")).bindSelected(
                    { settings.configurationMode != ConfigurationMode.DISABLED && settings.fixAllOnSave },
                    { settings.fixAllOnSave = it },
                ).component

                val link = ActionsOnSaveConfigurable.createGoToActionsOnSavePageLink()
                cell(link)
            }.enabledIf(!disabledConfiguration.selected)

            // *********************
            // Disable Nested Config row
            // *********************
            row {
                checkBox(OxlintBundle.message("oxc.disable.nested.config.label")).bindSelected(
                    { settings.disableNestedConfig },
                    { settings.disableNestedConfig = it },
                )
            }.enabledIf(!disabledConfiguration.selected)
            // *********************
            // Oxlint Fix Kind row
            // *********************
            row(OxlintBundle.message("oxc.fix.kind.label")) {
                // TODO: Probably a better way to do this map of enum to presentation text.
                val options = mapOf(
                    OxlintFixKind.SAFE_FIX to "Safe Fix",
                    OxlintFixKind.SAFE_FIX_OR_SUGGESTION to "Safe Fix or Suggestion",
                    OxlintFixKind.DANGEROUS_FIX to "Dangerous Fix",
                    OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION to "Dangerous Fix or Suggestion",
                    OxlintFixKind.NONE to "None",
                    OxlintFixKind.ALL to "All",
                )
                val reverseOptions = options.entries.associateBy({ it.value }, { it.key })

                comboBox(options.values).bindItem({
                    return@bindItem options[settings.fixKind]
                }, {
                    if (it != null) {
                        settings.fixKind = reverseOptions[it]!!
                    }
                })
            }.enabledIf(!disabledConfiguration.selected)

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
