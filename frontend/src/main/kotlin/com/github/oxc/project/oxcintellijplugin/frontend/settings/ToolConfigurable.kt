@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.settings

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.frontend.files.chooseBackendFile
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.isValidExtension
import com.intellij.ide.DataManager
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable
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
import com.intellij.openapi.options.ex.Settings
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
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.not
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun parseExtensions(text: String): List<String> =
  text.split(",").map(String::trim).filter(String::isNotEmpty)

/** Returns the first entry that isn't a valid extension, or null if they all are. */
fun findInvalidExtension(text: String): String? =
  text.split(",").map(String::trim).firstOrNull { !isValidExtension(it) }

/** Applies outlive the settings dialog, and a later load has to see what an earlier apply stored. */
@Service(Service.Level.PROJECT)
internal class OxcSettingsClient(private val project: Project, val scope: CoroutineScope) {

  private val order = Mutex()

  val projectId: ProjectId? = project.takeUnless { it.isDefault }?.projectId()

  suspend fun <T> load(load: suspend () -> T): T = order.withLock { load() }

  /** Takes its place in line before returning, so that a load started after this waits for it. */
  fun apply(failureMessageKey: String, apply: suspend () -> Unit) {
    scope.launch(Dispatchers.Default, CoroutineStart.UNDISPATCHED) {
      order.withLock { runReportingFailure(failureMessageKey, apply) }
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
      logger<OxcSettingsClient>().warn(exception)
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
abstract class ToolConfigurable<T : Any>(
  protected val project: Project,
  private val id: String,
  /** The start of the page's message keys. */
  private val prefix: String,
  initial: T,
) : SearchableConfigurable {

  private val client: OxcSettingsClient
    get() = project.service()

  protected var model: T = initial

  private var panel: DialogPanel? = null
  private var loadingPanel: JBLoadingPanel? = null
  private var disposable: Disposable? = null
  private var loading: Job? = null

  /** False until the backend's values are showing, and while they couldn't be loaded. */
  var isLoaded: Boolean = false
    private set

  protected abstract suspend fun loadSettings(projectId: ProjectId?): T

  protected abstract suspend fun applySettings(projectId: ProjectId?, settings: T)

  protected abstract fun Panel.createRows()

  override fun getId(): String = id

  override fun getDisplayName(): String = OxcFrontendBundle.message("$prefix.settings.name")

  override fun createComponent(): JComponent {
    val parent = Disposer.newDisposable("$id page").also { disposable = it }
    val dialogPanel = panel { createRows() }.also { panel = it }
    dialogPanel.registerValidators(parent)
    return JBLoadingPanel(BorderLayout(), parent).also {
      it.add(dialogPanel, BorderLayout.CENTER)
      loadingPanel = it
    }
  }

  override fun isModified(): Boolean = isLoaded && panel?.isModified() == true

  override fun apply() {
    if (!isLoaded) return

    val dialogPanel = panel ?: return
    dialogPanel.apply()
    val settings = model
    val client = client
    client.apply("$prefix.settings.apply.failed") { applySettings(client.projectId, settings) }
    // What's stored can be written differently from what was typed, such as spaces in the extensions.
    dialogPanel.reset()
  }

  override fun reset() {
    val dialogPanel = panel ?: return
    val client = client
    isLoaded = false
    loading?.cancel()
    loadingPanel?.startLoading()
    loading = client.scope.launch(Dispatchers.Default) {
      client.runReportingFailure("$prefix.settings.load.failed") {
        val settings = client.load { loadSettings(client.projectId) }
        // Any modality, because Settings opened from a modal dialog is modal too, and the page isn't in a
        // window yet when this is called, so its own modality would wait for that dialog to close. Only
        // the page changes here, which is safe in any modality.
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
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

  /** Adds the mode and manual path rows, and returns whether the tool is enabled. */
  protected fun Panel.modeRows(
    mode: () -> ConfigurationMode,
    setMode: (ConfigurationMode) -> Unit,
    binaryPath: MutableProperty<String>,
    configPath: MutableProperty<String>,
  ): ComponentPredicate {
    lateinit var disabled: Cell<JBRadioButton>
    lateinit var manual: Cell<JBRadioButton>

    fun modeProperty(value: ConfigurationMode) = MutableProperty(
      { mode() == value },
      { selected -> if (selected) setMode(value) },
    )

    buttonsGroup {
      row {
        disabled = radioButton(OxcFrontendBundle.message("$prefix.settings.mode.disabled"))
          .bindSelected(modeProperty(ConfigurationMode.DISABLED))
      }
      row {
        radioButton(OxcFrontendBundle.message("$prefix.settings.mode.automatic"))
          .bindSelected(modeProperty(ConfigurationMode.AUTOMATIC))
        cell(ContextHelpLabel.create(OxcFrontendBundle.message(
          "$prefix.settings.mode.automatic.help",
          ApplicationNamesInfo.getInstance().fullProductName,
        )))
      }
      row {
        manual = radioButton(OxcFrontendBundle.message("$prefix.settings.mode.manual"))
          .bindSelected(modeProperty(ConfigurationMode.MANUAL))
      }
    }

    indent {
      pathRow("$prefix.settings.binary.path", binaryPath).visibleIf(manual.selected)
      pathRow("$prefix.settings.config.path", configPath).visibleIf(manual.selected)
    }
    return disabled.selected.not()
  }

  /** [labelKey] names the row's label, and with `.chooser.title` the file chooser's title. */
  private fun Panel.pathRow(labelKey: String, property: MutableProperty<String>): Row =
    row(OxcFrontendBundle.message(labelKey)) {
      val field = TextFieldWithBrowseButton()
      field.addActionListener {
        chooseBackendFile(project, OxcFrontendBundle.message("$labelKey.chooser.title"), field.text)?.let { field.text = it }
      }
      cell(field)
        .align(AlignX.FILL)
        .bind({ it.text }, { component, value -> component.text = value }, property)
    }

  /** An empty list of extensions means [defaults], and is what's stored when the text matches them. */
  protected fun Panel.extensionsRow(
    enabled: ComponentPredicate,
    defaults: List<String>,
    extensions: () -> List<String>,
    setExtensions: (List<String>) -> Unit,
  ) {
    row(OxcFrontendBundle.message("settings.extensions")) {
      // Without a set width, the page grows as wide as the text and scrolls sideways.
      val cell = expandableTextField(::parseExtensions) { it.joinToString(",") }
        .columns(COLUMNS_MEDIUM)
        .align(AlignX.FILL)
        .bindText(
          { extensions().ifEmpty { defaults }.joinToString(",") },
          { text ->
            val parsed = parseExtensions(text)
            setExtensions(if (parsed == defaults) emptyList() else parsed)
          },
        )
        .validationOnInput { field: JBTextField ->
          findInvalidExtension(field.text)?.let { invalid ->
            ValidationInfo(OxcFrontendBundle.message("settings.extensions.invalid", invalid), field)
          }
        }
      cell.comment(
        // One message with the link in it, so a translation can put the link where its grammar needs it.
        OxcFrontendBundle.message("settings.extensions.comment"),
        // Shorter than the default, which is what would make the page wider than the dialog.
        45,
      )
      cell.comment!!.addHyperlinkListener { event ->
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED && event.description == "reset") {
          cell.component.text = defaults.joinToString(",")
        }
      }
    }.bottomGap(BottomGap.MEDIUM).enabledIf(enabled)
  }

  /**
   * Fix on save is only edited on the Actions on Save page, because that page is drawn by the backend. The
   * platform's link selects the page in the open settings, which a thin client's settings can't do for a
   * backend page. There the link would do nothing, so the row says where the page is instead.
   */
  protected fun Panel.fixOnSaveLinkRow(enabled: ComponentPredicate) {
    row {
      val link = cell(ActionsOnSaveConfigurable.createGoToActionsOnSavePageLink()).component
      val hint = comment(OxcFrontendBundle.message("settings.actions.on.save.hint")).visible(false).component
      // The settings are only known once the page is in their window.
      link.addHierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && link.isShowing) {
          val canGo = canGoToActionsOnSave(Settings.KEY.getData(DataManager.getInstance().getDataContext(link)))
          link.isVisible = canGo
          hint.isVisible = !canGo
        }
      }
    }.enabledIf(enabled)
  }
}

/** Whether the platform's Actions on Save link can select its page in [settings]. Unknown settings keep it. */
fun canGoToActionsOnSave(settings: Settings?): Boolean =
  settings == null || settings.find(ActionsOnSaveConfigurable.CONFIGURABLE_ID) != null
