package com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.isOxlintSupportedFile
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider

class OxlintLspIntegrationProvider : LspIntegrationProvider {

  override fun fileOpened(
    project: Project,
    file: VirtualFile,
    clientStarter: LspIntegrationProvider.LspClientStarter,
  ) {
    if (!isOxlintSupportedFile(file)) return

    val oxlint = OxlintPackage(project)
    val nodePackage = oxlint.find(file)
    val executable = nodePackage?.let(oxlint::executablePath)
    val root = nodePackage?.let(oxlint::serverRoot)
    if (executable == null || root == null) {
      thisLogger().debug(
        "Not starting Oxlint for ${file.path}: package=$nodePackage, executable=$executable, root=$root")
      return
    }

    clientStarter.ensureClientStarted(OxlintLspClientDescriptor(project, root, executable))
  }
}
