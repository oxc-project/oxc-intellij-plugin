package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile

/** The settings that the shared code reads. Each tool also has its own. */
interface ToolSettings {
  val configurationMode: ConfigurationMode
  val binaryPath: String?
  val configPath: String?

  val isEnabled: Boolean
    get() = configurationMode != ConfigurationMode.DISABLED

  val isFixAllOnSaveEnabled: Boolean

  /** Changes with every change to the settings, for caches that depend on them. */
  val modificationTracker: ModificationTracker

  fun isSupported(file: VirtualFile): Boolean

  fun updateFixAllOnSave(enabled: Boolean)
}
