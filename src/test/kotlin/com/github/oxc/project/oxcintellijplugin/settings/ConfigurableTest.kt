package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField

/** The Settings | Tools | Oxlint and Oxfmt pages. */
class ConfigurableTest : OxcLightTestCase() {

    fun testOxlintPageShowsAndAppliesSettings() {
        val settings = OxlintSettings.getInstance(project)
        settings.fixAllOnSave = true
        val configurable = OxlintConfigurable(project)
        try {
            configurable.createComponent()
            configurable.reset()

            assertFalse(configurable.isModified)
            assertTrue(configurable.fixAllOnSaveCheckBox.isSelected)

            configurable.fixAllOnSaveCheckBox.isSelected = false
            assertTrue(configurable.isModified)
            configurable.apply()
            assertFalse(settings.state.fixAllOnSave)

            // Rows other than the mode are disabled while Oxlint is disabled, so set the mode last.
            configurable.disabledConfiguration.isSelected = true
            configurable.apply()
            assertEquals(ConfigurationMode.DISABLED, settings.configurationMode)
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testOxfmtPageShowsAndAppliesSettings() {
        val settings = OxfmtSettings.getInstance(project)
        val configurable = OxfmtConfigurable(project)
        try {
            configurable.createComponent()
            configurable.reset()

            assertFalse(configurable.isModified)
            assertFalse(configurable.preferOxfmtCodeStyleSettingsCheckBox.isSelected)

            configurable.preferOxfmtCodeStyleSettingsCheckBox.isSelected = true
            configurable.fixAllOnSaveCheckBox.isSelected = true
            assertTrue(configurable.isModified)
            configurable.apply()

            assertTrue(settings.preferOxfmtCodeStyleSettings)
            assertTrue(settings.fixAllOnSave)
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testFixAllOnSaveCheckboxIsClearedWhileDisabled() {
        OxlintSettings.getInstance(project).fixAllOnSave = true
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED
        val configurable = OxlintConfigurable(project)
        try {
            configurable.createComponent()
            configurable.reset()

            assertFalse(configurable.fixAllOnSaveCheckBox.isSelected)
            assertTrue(configurable.disabledConfiguration.isSelected)
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testOxlintExtensionValidation() {
        val configurable = OxlintConfigurable(project)

        assertNull(validate(configurable, ".js, .ts,.vue"))
        assertNotNull(validate(configurable, "js"))
        assertNotNull(validate(configurable, ".js,"))
    }

    fun testOxfmtExtensionValidation() {
        val configurable = OxfmtConfigurable(project)

        assertNull(validate(configurable, ".js, .ts,.md"))
        assertNotNull(validate(configurable, "js"))
    }

    /**
     * QUESTIONABLE: the Oxlint page rejects multi-part extensions like `.d.ts`, but the Oxfmt page
     * accepts them (`.toml.example`) even though they can never match a file, see
     * `OxfmtSettingsTest.testMultiPartExtensionsInTheDefaultListNeverMatch`.
     */
    fun testMultiPartExtensionsAreAcceptedByOxfmtButNeverMatch() {
        assertNotNull(validate(OxlintConfigurable(project), ".d.ts"))
        assertNull(validate(OxfmtConfigurable(project), ".toml.example"))
    }

    fun testConfigurableIdsAndNames() {
        assertEquals("Oxlint", OxlintConfigurable(project).displayName)
        assertEquals("Oxfmt", OxfmtConfigurable(project).displayName)
        assertEquals("OxfmtConfigurable", OxfmtConfigurable(project).id)
    }

    /** Calls the page's private extension validator, which runs while the user types. */
    private fun validate(configurable: Any, text: String): ValidationInfo? {
        val method = configurable.javaClass.getDeclaredMethod("validateExtensions", JBTextField::class.java)
        method.isAccessible = true
        return method.invoke(configurable, JBTextField(text)) as ValidationInfo?
    }
}
