package com.github.oxc.project.oxcintellijplugin.oxlint.settings

import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintUnusedDisableDirectivesSeverity
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import java.nio.file.Files

class OxlintSettingsTest : OxcLightTestCase() {

    private val settings: OxlintSettings
        get() = OxlintSettings.getInstance(project)

    fun testDefaults() {
        assertEquals(ConfigurationMode.AUTOMATIC, settings.configurationMode)
        assertEquals("", settings.binaryPath)
        assertEquals("", settings.configPath)
        assertFalse(settings.disableNestedConfig)
        assertFalse(settings.fixAllOnSave)
        assertEquals(OxlintFixKind.SAFE_FIX, settings.fixKind)
        assertFalse(settings.typeAware)
        assertEquals(OxlintUnusedDisableDirectivesSeverity.ALLOW, settings.unusedDisableDirectivesSeverity)
        assertEquals(OxlintSettingsState.DEFAULT_EXTENSION_LIST, settings.supportedExtensions)
        assertTrue(settings.isEnabled())
    }

    fun testDefaultExtensionsCoverJavaScriptTypeScriptAndFrameworkFiles() {
        assertSameElements(OxlintSettingsState.DEFAULT_EXTENSION_LIST,
            ".js", ".jsx", ".cjs", ".mjs", ".ts", ".tsx", ".cts", ".mts", ".vue", ".svelte", ".astro")
    }

    fun testDisabledModeDisablesTheIntegration() {
        settings.configurationMode = ConfigurationMode.DISABLED

        assertFalse(settings.isEnabled())
    }

    fun testFixAllOnSaveIsOffWhileDisabledEvenIfStoredAsOn() {
        settings.fixAllOnSave = true
        assertTrue(settings.fixAllOnSave)

        settings.configurationMode = ConfigurationMode.DISABLED

        assertFalse(settings.fixAllOnSave)
        assertTrue("the stored value is kept for when the integration is re-enabled", settings.state.fixAllOnSave)
    }

    fun testFileSupportedUsesTheExtensionList() {
        for (extension in OxlintSettingsState.DEFAULT_EXTENSION_LIST) {
            assertTrue(extension, settings.fileSupported(file("index$extension")))
        }
        assertFalse(settings.fileSupported(file("styles.css")))
        assertFalse(settings.fileSupported(file("Makefile")))
    }

    fun testCustomExtensionList() {
        settings.supportedExtensions = mutableListOf(".js", ".md")

        assertTrue(settings.fileSupported(file("index.js")))
        assertTrue(settings.fileSupported(file("README.md")))
        assertFalse(settings.fileSupported(file("index.ts")))
    }

    /**
     * QUESTIONABLE: clearing every extension does not turn the linter off for all files; an empty
     * list silently falls back to the default list.
     */
    fun testEmptyExtensionListFallsBackToDefaults() {
        settings.supportedExtensions = mutableListOf()

        assertEquals(OxlintSettingsState.DEFAULT_EXTENSION_LIST, settings.supportedExtensions)
        assertTrue(settings.fileSupported(file("index.ts")))
    }

    /**
     * QUESTIONABLE: matching is case-sensitive, so `APP.JS` is not linted even though `.js` is
     * configured.
     */
    fun testExtensionMatchingIsCaseSensitive() {
        assertFalse(settings.fileSupported(file("APP.JS")))
    }

    fun testFixKindIsStoredInTheLegacyFlagsMap() {
        settings.fixKind = OxlintFixKind.DANGEROUS_FIX

        assertEquals(OxlintFixKind.DANGEROUS_FIX, settings.fixKind)
        assertEquals("DANGEROUS_FIX", settings.state.flags[OxlintSettings.FIX_KIND_KEY])
    }

    fun testFixKindStoredInLowerCaseIsRead() {
        settings.loadState(OxlintSettingsState().apply {
            flags[OxlintSettings.FIX_KIND_KEY] = "safe_fix_or_suggestion"
        })

        assertEquals(OxlintFixKind.SAFE_FIX_OR_SUGGESTION, settings.fixKind)
    }

    fun testInvalidFixKindFallsBackToSafeFix() {
        settings.loadState(OxlintSettingsState().apply { flags[OxlintSettings.FIX_KIND_KEY] = "bogus" })

        assertEquals(OxlintFixKind.SAFE_FIX, settings.fixKind)
    }

    fun testDisableNestedConfigIsStoredInTheLegacyFlagsMap() {
        settings.disableNestedConfig = true

        assertTrue(settings.disableNestedConfig)
        assertEquals("true", settings.state.flags[OxlintSettings.DISABLE_NESTED_CONFIG_KEY])
    }

    fun testConfigPathKeepsTheValueAsEntered() {
        val config = Files.createTempFile("custom", ".oxlintrc.json")
        try {
            settings.configPath = config.toString()
            assertEquals(config.toString(), settings.configPath)

            settings.configPath = "does/not/exist.json"
            assertEquals("does/not/exist.json", settings.configPath)
        } finally {
            Files.deleteIfExists(config)
        }
    }

    /** Projects configured by older plugin versions store `.idea/OxcSettings.xml` in this shape. */
    fun testDeserializesStoredProjectSettings() {
        val element = JDOMUtil.load("""
            <component name="OxcSettings">
              <option name="configurationMode" value="MANUAL" />
              <option name="configPath" value="/project/custom-oxlint.jsonc" />
              <option name="binaryPath" value="/project/node_modules/oxlint/bin/oxlint" />
              <option name="fixAllOnSave" value="true" />
              <option name="typeAware" value="true" />
              <option name="unusedDisableDirectives" value="DENY" />
            </component>
        """.trimIndent())

        settings.loadState(XmlSerializer.deserialize(element, OxlintSettingsState::class.java))

        assertEquals(ConfigurationMode.MANUAL, settings.configurationMode)
        assertEquals("/project/custom-oxlint.jsonc", settings.configPath)
        assertEquals("/project/node_modules/oxlint/bin/oxlint", settings.binaryPath)
        assertTrue(settings.fixAllOnSave)
        assertTrue(settings.typeAware)
        assertEquals(OxlintUnusedDisableDirectivesSeverity.DENY, settings.unusedDisableDirectivesSeverity)
    }

    fun testLegacyFlagsSurviveSerialization() {
        settings.fixKind = OxlintFixKind.DANGEROUS_FIX
        settings.disableNestedConfig = true

        val element = XmlSerializer.serialize(settings.state)
        settings.loadState(OxlintSettingsState())
        assertEquals(OxlintFixKind.SAFE_FIX, settings.fixKind)

        settings.loadState(XmlSerializer.deserialize(element, OxlintSettingsState::class.java))
        assertEquals(OxlintFixKind.DANGEROUS_FIX, settings.fixKind)
        assertTrue(settings.disableNestedConfig)
    }

    private fun file(name: String) = myFixture.addFileToProject(name, "").virtualFile
}
