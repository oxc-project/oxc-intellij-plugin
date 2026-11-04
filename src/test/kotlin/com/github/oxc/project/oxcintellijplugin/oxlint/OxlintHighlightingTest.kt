package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.extensions.configureByFileAndCheckLanguageServerHighlighting
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.TestFixtures

/**
 * Oxlint diagnostics shown in the editor, checked against `*.expected.js` files with highlighting
 * markup. Fixtures are under `testData/oxlint/highlighting`.
 */
class OxlintHighlightingTest : OxcLspTestCase() {

    private fun setUpHighlightingFixture(name: String) {
        val fixture = "oxlint/highlighting/$name"
        setUpFixture(fixture)
        myFixture.testDataPath = TestFixtures.testDataRoot.resolve(fixture).toString()
    }

    fun testNoConfigUsesDefaultRules() {
        setUpHighlightingFixture("no-config")

        myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
    }

    fun testRootFileUsesRootConfig() {
        setUpHighlightingFixture("nested-config")

        myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
    }

    fun testSubdirectoryFileUsesNestedConfig() {
        setUpHighlightingFixture("nested-config")

        myFixture.configureByFileAndCheckLanguageServerHighlighting("subdirectory/index.expected.js")
    }

    fun testSubdirectoryFileUsesRootConfigWhenNestedConfigDisabled() {
        setUpHighlightingFixture("nested-config")
        OxlintSettings.getInstance(project).disableNestedConfig = true

        // The root config reports unicorn/no-new-array as an error, the nested one as a warning.
        myFixture.configureByFileAndCheckLanguageServerHighlighting("subdirectory/index.root-config.expected.js")
    }

    fun testManualConfigPathIsUsed() {
        setUpHighlightingFixture("custom-config")
        OxlintSettings.getInstance(project).apply {
            configurationMode = ConfigurationMode.MANUAL
            configPath = projectDir.resolve("custom-oxlint.jsonc").toString()
        }

        myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
    }
}
