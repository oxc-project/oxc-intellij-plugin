package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.extensions.configureByFileAndCheckLanguageServerHighlighting
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

/** Starts a real Oxlint server. Needs Node on PATH and `npm ci` run in the fixture folder. */
@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class NoConfigHighlightingTest : OxcFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
  }

  fun testRootFileHighlighting() {
    myFixture.copyDirectoryToProject("no-config", "")

    myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
  }
}
