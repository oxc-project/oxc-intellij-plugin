package com.github.oxc.project.oxcintellijplugin.extensions

import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Checks the file's highlighting against its expected markup, retrying until the language server has
 * started and its diagnostics have been applied. Highlighting itself asks the server for diagnostics,
 * so there is no event to wait for first.
 */
fun CodeInsightTestFixture.configureByFileAndCheckLanguageServerHighlighting(
  filePath: String,
  timeout: Duration = 30.seconds,
) {
  configureByFile(filePath)

  // Reads the markup out of the document, so this can only happen once.
  val expectedHighlighting = ExpectedHighlightingData(editor.document, true, true, true, false)
  expectedHighlighting.init()

  val deadline = TimeSource.Monotonic.markNow() + timeout
  while (true) {
    try {
      (this as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(expectedHighlighting)
      return
    } catch (e: AssertionError) {
      if (deadline.hasPassedNow()) throw e
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(250.milliseconds.inWholeMilliseconds)
  }
}
