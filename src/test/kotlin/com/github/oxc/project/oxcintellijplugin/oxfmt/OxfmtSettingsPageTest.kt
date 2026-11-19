package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.isOxfmtSettingsPage
import com.github.oxc.project.oxcintellijplugin.frontend.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JComponent

/** The code style status bar's "Open Oxfmt Settings". */
class OxfmtSettingsPageTest : BasePlatformTestCase() {

  fun testFindsTheOxfmtPage() {
    assertTrue(isOxfmtSettingsPage(OxfmtConfigurable(project)))
    assertFalse(isOxfmtSettingsPage(OxlintConfigurable(project)))
  }

  /** The display name is translated, and another page could have the same one. */
  fun testGoesByTheIdNotTheDisplayName() {
    assertFalse(isOxfmtSettingsPage(PageNamed("Oxfmt")))
  }

  private class PageNamed(private val name: String) : SearchableConfigurable {
    override fun getId(): String = "not.oxfmt"
    override fun getDisplayName(): String = name
    override fun createComponent(): JComponent? = null
    override fun isModified(): Boolean = false
    override fun apply() = Unit
  }
}
