package com.github.oxc.project.oxcintellijplugin.backend.config

import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/** The Oxc icon for each tool's config files and manual config paths. */
class OxcConfigFileIconProvider : FileIconProvider, DumbAware {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? =
    OxcIcons.OxcRound.takeIf { !file.isDirectory && ToolDefinition.all.any { it.isConfigFile(project, file) } }
}
