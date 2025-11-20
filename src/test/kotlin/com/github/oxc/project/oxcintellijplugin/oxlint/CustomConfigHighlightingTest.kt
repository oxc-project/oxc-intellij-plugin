package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.extensions.configureByFileAndCheckLanguageServerHighlighting
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class CustomConfigHighlightingTest :
    CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
        myFixture.testDataPath = "src/test/testData/oxlint/highlighting"

        val oxlintSettings = OxlintSettings.getInstance(myFixture.project)
        oxlintSettings.configPath = "${myFixture.tempDirPath}/custom-oxlint.jsonc"
        oxlintSettings.configurationMode = ConfigurationMode.MANUAL
    }

    fun testRootFileHighlighting() {
        myFixture.copyDirectoryToProject("custom-config", "")

        myFixture.configureByFileAndCheckLanguageServerHighlighting(OxlintLspServerDescriptor::class.java, "index.expected.js")
    }
}
