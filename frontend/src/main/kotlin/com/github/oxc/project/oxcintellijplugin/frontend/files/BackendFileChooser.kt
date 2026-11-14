@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.frontend.files

import com.github.oxc.project.oxcintellijplugin.frontend.OxcFrontendBundle
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFileEntry
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFolder
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.project.projectId
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Asks for a file on the machine that runs the language servers. A monolith uses the platform's file
 * chooser. A thin client's chooser shows its own machine, and its Host tab had no roots when connected
 * to a Linux backend in 2026.3, so a thin client browses over RPC instead.
 */
fun chooseBackendFile(project: Project, title: String, currentPath: String): String? {
  if (IdeProductMode.isFrontend) {
    val dialog = BackendFileChooserDialog(project, title, currentPath)
    return if (dialog.showAndGet()) dialog.selectedPath else null
  }

  val descriptor = FileChooserDescriptorFactory.singleFile().withTitle(title)
  val current = currentPath.takeIf { it.isNotBlank() }?.let(LocalFileSystem.getInstance()::findFileByPath)
  return FileChooser.chooseFile(descriptor, project, current)?.path
}

@Service(Service.Level.PROJECT)
private class BackendFileChooserScope(val scope: CoroutineScope)

private class BackendFileChooserDialog(
  private val project: Project,
  title: String,
  private val initialPath: String,
) : DialogWrapper(project, true) {

  private val pathField = JBTextField()
  private val entries = DefaultListModel<BackendFileEntry>()
  private val list = JBList(entries)
  private val errorLabel = JBLabel().apply {
    foreground = UIUtil.getErrorForeground()
    isVisible = false
  }
  private var loading: Job? = null

  var selectedPath: String? = null
    private set

  init {
    this.title = title
    isOKActionEnabled = false
    init()
    load(initialPath)
  }

  override fun getPreferredFocusedComponent(): JComponent = list

  override fun createCenterPanel(): JComponent {
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    list.cellRenderer = EntryRenderer()
    list.addListSelectionListener { isOKActionEnabled = list.selectedValue?.isDirectory == false }
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        openSelection()
        return true
      }
    }.installOn(list)
    list.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == KeyEvent.VK_ENTER) {
          event.consume()
          openSelection()
        }
      }
    })
    pathField.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == KeyEvent.VK_ENTER) {
          event.consume()
          load(pathField.text)
        }
      }
    })

    return JPanel(BorderLayout(0, JBUI.scale(8))).apply {
      add(pathField, BorderLayout.NORTH)
      add(JBScrollPane(list).apply { preferredSize = JBUI.size(560, 360) }, BorderLayout.CENTER)
      add(errorLabel, BorderLayout.SOUTH)
    }
  }

  override fun doOKAction() {
    selectedPath = list.selectedValue?.takeUnless { it.isDirectory }?.path ?: return
    super.doOKAction()
  }

  override fun dispose() {
    loading?.cancel()
    super.dispose()
  }

  private fun openSelection() {
    val entry = list.selectedValue ?: return
    if (entry.isDirectory) load(entry.path) else doOKAction()
  }

  private fun load(path: String?) {
    loading?.cancel()
    val modality = ModalityState.stateForComponent(contentPanel).asContextElement()
    loading = project.service<BackendFileChooserScope>().scope.launch(Dispatchers.Default) {
      val projectId = project.takeUnless { it.isDefault }?.projectId()
      val folder = try {
        durable { BackendFilesApi.getInstance().listFolder(projectId, path) }
      }
      catch (exception: CancellationException) {
        throw exception
      }
      catch (exception: Exception) {
        // Shown in the dialog, where the lister's own errors go, rather than as a balloon behind it.
        logger<BackendFileChooserDialog>().warn(exception)
        BackendFolder(
          path = path.orEmpty(),
          parent = null,
          entries = emptyList(),
          error = OxcFrontendBundle.message("files.chooser.load.failed", exception.message.orEmpty()),
        )
      }
      withContext(Dispatchers.EDT + modality) { show(folder, path) }
    }
  }

  private fun show(folder: BackendFolder, requestedPath: String?) {
    pathField.text = folder.path
    entries.clear()
    folder.parent?.let {
      entries.addElement(BackendFileEntry(OxcFrontendBundle.message("files.chooser.parent"), it, true))
    }
    folder.entries.forEach(entries::addElement)
    errorLabel.text = folder.error.orEmpty()
    errorLabel.isVisible = folder.error != null
    list.setSelectedValue(folder.entries.firstOrNull { it.path == requestedPath }, true)
  }

  private class EntryRenderer : ColoredListCellRenderer<BackendFileEntry>() {
    override fun customizeCellRenderer(
      list: JList<out BackendFileEntry>,
      value: BackendFileEntry,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
      icon = if (value.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type
      append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
  }
}
