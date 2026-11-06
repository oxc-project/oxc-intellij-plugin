package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.testFramework.PlatformTestUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Waits for what a language server does in the background, while letting the UI thread keep working. */
fun waitUntil(description: String, timeout: Duration = 30.seconds, condition: () -> Boolean) {
  val deadline = TimeSource.Monotonic.markNow() + timeout
  while (true) {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    if (condition()) return
    if (deadline.hasPassedNow()) throw AssertionError("Timed out waiting until $description")
    Thread.sleep(100)
  }
}
