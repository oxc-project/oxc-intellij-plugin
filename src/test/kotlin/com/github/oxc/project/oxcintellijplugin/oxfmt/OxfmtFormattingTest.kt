package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase

/** Oxfmt config file resolution against the real language server. */
class OxfmtFormattingTest : OxcLspTestCase() {

    override fun setUp() {
        super.setUp()
        setUpFixture("oxfmt/formatting/nested-config")
    }

    fun testRootFileUsesRootConfig() {
        assertEquals("const greeting = 'Happy developing ✨';\n", formatWithServer(openFile("index.js")))
    }

    fun testSubdirectoryFileUsesNestedConfig() {
        assertEquals("const greeting = \"Happy developing ✨\"\n", formatWithServer(openFile("subdirectory/index.js")))
    }

    fun testSubdirectoryFileUsesRootConfigWhenNestedConfigDisabled() {
        OxfmtSettings.getInstance(project).disableNestedConfig = true

        assertEquals("const greeting = 'Happy developing ✨';\n", formatWithServer(openFile("subdirectory/index.js")))
    }

    fun testManualConfigPathIsUsed() {
        OxfmtSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            configPath = projectDir.resolve("subdirectory/.oxfmtrc.json").toString()
        }

        assertEquals("const greeting = \"Happy developing ✨\"\n", formatWithServer(openFile("index.js")))
    }
}
