package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtConfigNotice
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The status bar's actions run here on the backend, where the platform asks for them, so what they report
 * goes to the frontend's notifier, which shows it in the `Oxc` group.
 */
class OxfmtCodeStyleStatusBarUiContributor : CodeStyleStatusBarUIContributor {

  override fun areActionsAvailable(file: VirtualFile): Boolean = true

  override fun getActions(file: PsiFile): Array<AnAction> = arrayOf(
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.open.configuration.file.label")) {
      val path = file.virtualFile ?: return@create
      file.project.service<OxfmtConfigOpener>().open(path)
    },
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.open.settings.label")) {
      openOxfmtSettings(file.project)
    },
  )

  override fun getTooltip(): String? = null

  override fun createDisableAction(project: Project): AnAction =
    DumbAwareAction.create(OxcBackendBundle.message("oxfmt.action.disable.for.project.label")) {
      disableOxfmtCodeStyle(project)
      OxfmtJsConfigLoader.getInstance(project).send(OxfmtConfigNotice.CodeStyleDisabled)
    }

  override fun getActionGroupTitle(): String = OxcBackendBundle.message("oxfmt.code.style.status.bar.action.group.title")
}

/** By the page's id, because its display name is translated. Public for tests. */
fun isOxfmtSettingsPage(configurable: Configurable): Boolean =
  (configurable as? SearchableConfigurable)?.id == OxfmtDefinition.configurableId

private fun openOxfmtSettings(project: Project) {
  ShowSettingsUtil.getInstance().showSettingsDialog(project, ::isOxfmtSettingsPage, null)
}

/**
 * Opens the config that applies to a file. The lookup runs off the EDT, because without a running server
 * it finds the oxfmt package on disk, which is slow over `\\wsl$` or a remote file system.
 */
@Service(Service.Level.PROJECT)
internal class OxfmtConfigOpener(private val project: Project, private val scope: CoroutineScope) {

  fun open(file: VirtualFile) {
    scope.launch {
      val config = readAction { findOxfmtConfig(project, file) }
      if (config == null) {
        OxfmtJsConfigLoader.getInstance(project).send(OxfmtConfigNotice.ConfigNotFound(file.name))
        return@launch
      }
      withContext(Dispatchers.EDT) { OpenFileDescriptor(project, config).navigate(true) }
    }
  }
}
