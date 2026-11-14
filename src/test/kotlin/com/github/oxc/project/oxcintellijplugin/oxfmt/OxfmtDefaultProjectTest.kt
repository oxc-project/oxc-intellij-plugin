package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtCodeStyleSettingsModifier
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Settings for New Projects can turn the overlay on for the default project, whose code style preview
 * then asks the modifier. The default project has no LSP manager, and asking for it fails.
 */
class OxfmtDefaultProjectTest : BasePlatformTestCase() {

  private val defaultProject: Project
    get() = ProjectManager.getInstance().defaultProject

  private var previous = false

  override fun setUp() {
    super.setUp()
    val settings = OxfmtSettings.getInstance(defaultProject)
    previous = settings.state.preferOxfmtCodeStyleSettings
    settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = true))
  }

  // The default project's settings are app-wide, so they go back as they were.
  override fun tearDown() {
    try {
      val settings = OxfmtSettings.getInstance(defaultProject)
      settings.update(settings.toData().copy(preferOxfmtCodeStyleSettings = previous))
    }
    finally {
      super.tearDown()
    }
  }

  fun testTheDefaultProjectIsNeverOverridden() {
    assertFalse(OxfmtCodeStyleSettingsModifier().mayOverrideSettingsOf(defaultProject))
  }

  fun testAPreviewFileInTheDefaultProjectGetsNoOverlay() {
    val file = previewFile("a.js")
    assertNotNull("The preview file should have a virtual file", file.virtualFile)

    val applied = ReadAction.compute<Boolean, Exception> {
      val copy = TransientCodeStyleSettings(file.virtualFile, defaultProject, CodeStyle.getSettings(defaultProject))
      OxfmtCodeStyleSettingsModifier().modifySettings(copy, file)
    }

    assertFalse(applied)
  }

  /** The refresher listens to every file change. It mustn't ask the default project's LSP manager. */
  fun testAConfigChangeDoesNotReachTheDefaultProjectsLspManager() {
    val config = myFixture.tempDirFixture.createFile(".oxfmtrc.json", "{}")
    WriteAction.runAndWait<Exception> { VfsUtil.saveText(config, """{ "useTabs": true }""") }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  /** Like the code style page's preview: a file that exists only in memory, in the default project. */
  private fun previewFile(name: String): PsiFile =
    ReadAction.compute<PsiFile, Exception> {
      PsiFileFactory.getInstance(defaultProject)
        .createFileFromText(name, FileTypeManager.getInstance().getFileTypeByFileName(name), "", 0, true)
    }
}
