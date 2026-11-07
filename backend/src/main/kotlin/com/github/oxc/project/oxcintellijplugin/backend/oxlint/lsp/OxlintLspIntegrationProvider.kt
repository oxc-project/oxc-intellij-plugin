package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider

class OxlintLspIntegrationProvider : LspIntegrationProvider {

  override fun fileOpened(
    project: Project,
    file: VirtualFile,
    clientStarter: LspIntegrationProvider.LspClientStarter,
  ) {
    createDescriptor(project, file)?.let(clientStarter::ensureClientStarted)
  }

  /** Returns null if Oxlint shouldn't run for the file, or there's nothing to run. */
  fun createDescriptor(project: Project, file: VirtualFile): OxlintLspClientDescriptor? {
    val settings = OxlintSettings.getInstance(project)
    if (!settings.isEnabled || !settings.isSupported(file)) return null

    val oxlint = OxlintPackage(project)
    val nodePackage = oxlint.find(file)
    val manualBinary = settings.state.binaryPath?.takeIf {
      settings.state.configurationMode == ConfigurationMode.MANUAL && it.isNotBlank()
    }
    val executable = manualBinary ?: nodePackage?.let(oxlint::executablePath)
    val root = nodePackage?.let(oxlint::serverRoot)
               ?: ProjectFileIndex.getInstance(project).getContentRootForFile(file).takeIf { manualBinary != null }
    if (executable == null || root == null) {
      thisLogger().debug(
        "Not starting Oxlint for ${file.path}: package=$nodePackage, executable=$executable, root=$root")
      return null
    }

    return OxlintLspClientDescriptor(project, root, executable)
  }
}
