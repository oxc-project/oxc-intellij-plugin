package com.github.oxc.project.oxcintellijplugin.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer

class OxfmtSettingsTest : OxcLightTestCase() {

    private val settings: OxfmtSettings
        get() = OxfmtSettings.getInstance(project)

    fun testDefaults() {
        assertEquals(ConfigurationMode.AUTOMATIC, settings.configurationMode)
        assertEquals("", settings.binaryPath)
        assertEquals("", settings.configPath)
        assertFalse(settings.disableNestedConfig)
        assertFalse(settings.fixAllOnSave)
        assertFalse(settings.preferOxfmtCodeStyleSettings)
        assertEquals(OxfmtSettingsState.DEFAULT_EXTENSION_LIST, settings.supportedExtensions)
        assertTrue(settings.isEnabled())
    }

    fun testFixAllOnSaveIsOffWhileDisabledEvenIfStoredAsOn() {
        settings.fixAllOnSave = true
        settings.configurationMode = ConfigurationMode.DISABLED

        assertFalse(settings.fixAllOnSave)
        assertFalse(settings.isEnabled())
    }

    fun testCommonFileTypesAreSupportedByDefault() {
        val names = listOf(
            "index.js", "index.jsx", "index.cjs", "index.mjs", "index.ts", "index.tsx", "index.cts", "index.mts",
            "data.json", "tsconfig.jsonc", "data.json5", "page.html", "App.vue", "App.svelte", "view.hbs",
            "styles.css", "styles.scss", "styles.less", "schema.graphql", "README.md", "page.mdx",
            "config.yaml", "config.yml", "Cargo.toml",
        )
        for (name in names) {
            assertTrue(name, settings.fileSupported(file(name)))
        }
    }

    fun testUnlistedFileTypesAreNotSupported() {
        assertFalse(settings.fileSupported(file("main.rs")))
        assertFalse(settings.fileSupported(file("Makefile")))
    }

    /**
     * QUESTIONABLE: the default list contains multi-part extensions (`.toml.example`,
     * `.json.example`, `.tfstate.backup`, `.start.frag`, `.end.frag`), but matching uses
     * `VirtualFile.extension`, which is only the last segment (`example`, `backup`, `frag`).
     * These entries can never match, so such files are never formatted.
     */
    fun testMultiPartExtensionsInTheDefaultListNeverMatch() {
        assertTrue(OxfmtSettingsState.DEFAULT_EXTENSION_LIST.contains(".toml.example"))
        assertTrue(OxfmtSettingsState.DEFAULT_EXTENSION_LIST.contains(".json.example"))
        assertTrue(OxfmtSettingsState.DEFAULT_EXTENSION_LIST.contains(".tfstate.backup"))
        assertTrue(OxfmtSettingsState.DEFAULT_EXTENSION_LIST.contains(".start.frag"))

        assertFalse(settings.fileSupported(file("Cargo.toml.example")))
        assertFalse(settings.fileSupported(file("settings.json.example")))
        assertFalse(settings.fileSupported(file("terraform.tfstate.backup")))
        assertFalse(settings.fileSupported(file("intro.start.frag")))
    }

    /**
     * QUESTIONABLE: clearing every extension does not turn formatting off for all files; an empty
     * list silently falls back to the default list.
     */
    fun testEmptyExtensionListFallsBackToDefaults() {
        settings.supportedExtensions = mutableListOf()

        assertEquals(OxfmtSettingsState.DEFAULT_EXTENSION_LIST, settings.supportedExtensions)
    }

    fun testCustomExtensionList() {
        settings.supportedExtensions = mutableListOf(".ts")

        assertTrue(settings.fileSupported(file("index.ts")))
        assertFalse(settings.fileSupported(file("index.js")))
    }

    fun testPackageFallsBackToAnEmptyPackageWhenOxfmtIsNotInstalled() {
        assertEquals(OxfmtPackage.EMPTY_NODE_PACKAGE, settings.getPackage(null))
        assertEquals(OxfmtPackage.EMPTY_NODE_PACKAGE, settings.nodePackageRef.constantPackage)
        assertSame(settings, settings.withLinterPackage(settings.nodePackageRef))
    }

    fun testDeserializesStoredProjectSettings() {
        val element = JDOMUtil.load("""
            <component name="OxfmtSettings">
              <option name="configurationMode" value="MANUAL" />
              <option name="configPath" value="/project/.oxfmtrc.json" />
              <option name="binaryPath" value="/project/node_modules/oxfmt/bin/oxfmt" />
              <option name="disableNestedConfig" value="true" />
              <option name="fixAllOnSave" value="true" />
              <option name="preferOxfmtCodeStyleSettings" value="true" />
              <option name="supportedExtensions">
                <list>
                  <option value=".ts" />
                </list>
              </option>
            </component>
        """.trimIndent())

        settings.loadState(XmlSerializer.deserialize(element, OxfmtSettingsState::class.java))

        assertEquals(ConfigurationMode.MANUAL, settings.configurationMode)
        assertEquals("/project/.oxfmtrc.json", settings.configPath)
        assertEquals("/project/node_modules/oxfmt/bin/oxfmt", settings.binaryPath)
        assertTrue(settings.disableNestedConfig)
        assertTrue(settings.fixAllOnSave)
        assertTrue(settings.preferOxfmtCodeStyleSettings)
        assertEquals(listOf(".ts"), settings.supportedExtensions)
    }

    private fun file(name: String) = myFixture.addFileToProject(name, "").virtualFile
}
