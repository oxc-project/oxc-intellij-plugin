@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.files.chooseBackendFile
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.DEFAULT_OXLINT_EXTENSIONS
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintUnusedDisableDirectives
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

private val EXTENSION_PATTERN = Regex("^\\.[a-zA-Z0-9]+$")

fun parseExtensions(text: String): List<String> =
  text.split(",").map(String::trim).filter(String::isNotEmpty)

/** Returns the first entry that isn't a valid extension, or null if they all are. */
fun findInvalidExtension(text: String): String? =
  text.split(",").map(String::trim).firstOrNull { !EXTENSION_PATTERN.matches(it) }

/** Applies outlive the settings dialog, and a later load has to see what an earlier apply stored. */
@Service(Service.Level.PROJECT)
private class OxlintSettingsClient(private val project: Project, val scope: CoroutineScope) {

  private val order = Mutex()
  private val projectId: ProjectId? = project.takeUnless { it.isDefault }?.projectId()

  suspend fun load(): OxlintSettingsData =
    order.withLock { durable { OxlintSettingsApi.getInstance().load(projectId) } }

  /** Takes its place in line before returning, so that a load started after this waits for it. */
  fun apply(settings: OxlintSettingsData) {
    scope.launch(Dispatchers.Default, CoroutineStart.UNDISPATCHED) {
      order.withLock {
        runReportingFailure("oxlint.settings.apply.failed") {
          durable { OxlintSettingsApi.getInstance().apply(projectId, settings) }
        }
      }
    }
  }

  suspend fun runReportingFailure(messageKey: String, action: suspend () -> Unit) {
    try {
      action()
    }
    catch (exception: CancellationException) {
      throw exception
    }
    catch (exception: Exception) {
      logger<OxlintSettingsClient>().warn(exception)
      NotificationGroupManager.getInstance().getNotificationGroup("Oxc")
        .createNotification(OxcFrontendBundle.message(messageKey), NotificationType.ERROR)
        .notify(project)
    }
  }
}

/**
 * The page shows a copy of the settings, loaded from the backend each time it opens. Nothing can wait
 * for the backend on the UI thread, so it shows a loading state until the copy arrives.
 */
class OxlintConfigurable(private val project: Project) : SearchableConfigurable {

  private val client: OxlintSettingsClient
    get() = project.service()

  private var model = OxlintSettingsData()
  private var panel: DialogPanel? = null
  private var loadingPanel: JBLoadingPanel? = null
  private var disposable: Disposable? = null
  private var loading: Job? = null

  /** False until the backend's values are showing, and while they couldn't be loaded. */
  var isLoaded: Boolean = false
    private set

  override fun getId(): String = "OxlintConfigurable"

  override fun getDisplayName(): String = OxcFrontendBundle.message("oxlint.settings.name")

  override fun getHelpTopic(): String = "reference.settings.oxc"

  override fun createComponent(): JComponent {
    val parent = Disposer.newDisposable("Oxlint settings page").also { disposable = it }
    val dialogPanel = createPanel().also { panel = it }
    dialogPanel.registerValidators(parent)
    return JBLoadingPanel(BorderLayout(), parent).also {
      it.add(dialogPanel, BorderLayout.CENTER)
      loadingPanel = it
    }
  }

  override fun isModified(): Boolean = isLoaded && panel?.isModified() == true

  override fun apply() {
    if (!isLoaded) return

    panel?.apply()
    client.apply(model)
  }

  override fun reset() {
    val dialogPanel = panel ?: return
    isLoaded = false
    loading?.cancel()
    loadingPanel?.startLoading()
    val modality = ModalityState.stateForComponent(dialogPanel).asContextElement()
    loading = client.scope.launch(Dispatchers.Default) {
      client.runReportingFailure("oxlint.settings.load.failed") {
        val settings = client.load()
        withContext(Dispatchers.EDT + modality) {
          model = settings
          dialogPanel.reset()
          isLoaded = true
          loadingPanel?.stopLoading()
        }
      }
    }
  }

  override fun disposeUIResources() {
    loading?.cancel()
    disposable?.let(Disposer::dispose)
    disposable = null
    panel = null
    loadingPanel = null
  }

  private fun createPanel(): DialogPanel = panel {
    lateinit var disabled: Cell<JBRadioButton>
    lateinit var manual: Cell<JBRadioButton>

    buttonsGroup {
      row {
        disabled = radioButton(OxcFrontendBundle.message("oxlint.settings.mode.disabled"))
          .bindSelected(modeProperty(ConfigurationMode.DISABLED))
      }
      row {
        radioButton(OxcFrontendBundle.message("oxlint.settings.mode.automatic"))
          .bindSelected(modeProperty(ConfigurationMode.AUTOMATIC))
        cell(ContextHelpLabel.create(OxcFrontendBundle.message(
          "oxlint.settings.mode.automatic.help",
          ApplicationNamesInfo.getInstance().fullProductName,
        )))
      }
      row {
        manual = radioButton(OxcFrontendBundle.message("oxlint.settings.mode.manual"))
          .bindSelected(modeProperty(ConfigurationMode.MANUAL))
      }
    }

    val enabled = disabled.selected.not()
    indent {
      pathRow(
        OxcFrontendBundle.message("oxlint.settings.binary.path"),
        OxcFrontendBundle.message("oxlint.settings.binary.path.comment"),
        MutableProperty({ model.binaryPath }, { model = model.copy(binaryPath = it) }),
      ).visibleIf(manual.selected)
      pathRow(
        OxcFrontendBundle.message("oxlint.settings.config.path"),
        null,
        MutableProperty({ model.configPath }, { model = model.copy(configPath = it) }),
      ).visibleIf(manual.selected)
    }

    row(OxcFrontendBundle.message("oxlint.settings.unused.disable.directives")) {
      comboBox(OxlintUnusedDisableDirectives.entries, textListCellRenderer {
        it?.let { value -> OxcFrontendBundle.message("oxlint.settings.unused.disable.directives.${value.serverValue}") }
      }).bindItem(
        { model.unusedDisableDirectives },
        { model = model.copy(unusedDisableDirectives = it ?: OxlintUnusedDisableDirectives.ALLOW) },
      )
    }.enabledIf(enabled)

    extensionsRow(enabled)

    row {
      checkBox(OxcFrontendBundle.message("oxlint.settings.type.aware"))
        .bindSelected({ model.typeAware }, { model = model.copy(typeAware = it) })
    }.enabledIf(enabled)

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

  private fun modeProperty(mode: ConfigurationMode) = MutableProperty(
    { model.configurationMode == mode },
    { selected -> if (selected) model = model.copy(configurationMode = mode) },
  )

  private fun Panel.pathRow(label: String, comment: String?, property: MutableProperty<String>): Row =
    row(label) {
      val field = TextFieldWithBrowseButton()
      field.addActionListener {
        chooseBackendFile(project, label.removeSuffix(":"), field.text)?.let { field.text = it }
      }
      cell(field)
        .align(AlignX.FILL)
        .bind({ it.text }, { component, value -> component.text = value }, property)
        .also { if (comment != null) it.comment(comment) }
    }

  private fun Panel.extensionsRow(enabled: ComponentPredicate) {
    row(OxcFrontendBundle.message("oxlint.settings.extensions")) {
      val cell = expandableTextField(::parseExtensions) { it.joinToString(",") }
        .align(AlignX.FILL)
        .bindText(
          { model.supportedExtensions.ifEmpty { DEFAULT_OXLINT_EXTENSIONS }.joinToString(",") },
          { text ->
            val extensions = parseExtensions(text)
            model = model.copy(
              supportedExtensions = if (extensions == DEFAULT_OXLINT_EXTENSIONS) emptyList() else extensions)
          },
        )
        .validationOnInput { field: JBTextField ->
          findInvalidExtension(field.text)?.let { invalid ->
            ValidationInfo(OxcFrontendBundle.message("oxlint.settings.extensions.invalid", invalid), field)
          }
        }
      cell.comment(
        OxcFrontendBundle.message("oxlint.settings.extensions.comment") + " <a href=\"reset\">" +
        OxcFrontendBundle.message("oxlint.settings.extensions.reset") + "</a>"
      )
      cell.comment!!.addHyperlinkListener { event ->
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "reset") {
          cell.component.text = DEFAULT_OXLINT_EXTENSIONS.joinToString(",")
        }
      }
    }.bottomGap(BottomGap.MEDIUM).enabledIf(enabled)
  }
}
