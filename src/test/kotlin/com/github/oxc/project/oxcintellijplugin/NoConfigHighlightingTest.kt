package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.extensions.configureByFileAndCheckLanguageServerHighlighting
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

@TestDataPath("\$CONTENT_ROOT/testData/highlighting/lint")
class NoConfigHighlightingTest :
    CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
        myFixture.testDataPath = "src/test/testData/highlighting/lint"
    }

    fun testRootFileHighlighting() {
        myFixture.copyDirectoryToProject("no-config", "")

        myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
    }
}
