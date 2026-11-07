package com.github.oxc.project.oxcintellijplugin.testutil

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import junit.framework.TestCase
import kotlin.io.path.Path

/** Shows that [assertDoesNotBlock] can fail, because a check that can't fail wouldn't prove anything. */
class BlockingCheckTest : TestCase() {

  fun testPlainCodeDoesNotCount() {
    assertEquals("abc", assertDoesNotBlock { listOf("a", "b", "c").joinToString("") })
  }

  fun testStartingAProcessIsFound() {
    val java = Path(System.getProperty("java.home"), "bin", "java").toString()

    assertFound("jdk.ProcessStart") { ProcessBuilder(java, "-version").start() }
  }

  fun testWaitingForAFutureIsFound() {
    // The timer starts inside the check. Otherwise it can finish while a slow machine starts the recording.
    assertFound("jdk.ThreadPark") { CompletableFuture<Unit>().completeOnTimeout(Unit, 100, TimeUnit.MILLISECONDS).get() }
  }

  fun testSleepingIsFound() {
    assertFound("jdk.ThreadSleep") { Thread.sleep(50) }
  }

  private fun assertFound(eventName: String, block: () -> Unit) {
    val failure = runCatching { assertDoesNotBlock(block) }.exceptionOrNull()

    assertNotNull("Expected $eventName to be found", failure)
    assertTrue(failure!!.message!!, eventName in failure.message!!)
  }
}
