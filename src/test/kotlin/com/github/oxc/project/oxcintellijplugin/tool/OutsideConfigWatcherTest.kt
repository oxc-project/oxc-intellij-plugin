package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.backend.tool.OutsideConfigWatcher
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class OutsideConfigWatcherTest : BasePlatformTestCase() {

  private val watcher: OutsideConfigWatcher
    get() = project.service<OutsideConfigWatcher>()

  private lateinit var folder: Path

  override fun setUp() {
    super.setUp()
    // A new folder, so the VFS hasn't loaded it and the watch has to refresh.
    folder = Files.createTempDirectory("oxc-outside-config")
  }

  override fun tearDown() {
    try {
      watcher.watch(OxcTool.OXLINT, null)
      FileUtil.delete(folder)
    }
    finally {
      super.tearDown()
    }
  }

  fun testWatchedFileIsReported() {
    val file = createFile("a.json")

    PlatformTestUtil.waitForFuture(watchInBackground(file).done, 30_000)

    assertTrue(watcher.isWatched(OxcTool.OXLINT, file))
    assertFalse(watcher.isWatched(OxcTool.OXLINT, folder.resolve("b.json").toString()))
    assertFalse(watcher.isWatched(OxcTool.OXFMT, file))
  }

  fun testUnwatchingForgetsTheFile() {
    val file = createFile("a.json")
    PlatformTestUtil.waitForFuture(watchInBackground(file).done, 30_000)

    watcher.watch(OxcTool.OXLINT, null)

    assertFalse(watcher.isWatched(OxcTool.OXLINT, file))
  }

  /**
   * A refresh has to wait for the write lock, and the VFS listener asks [OutsideConfigWatcher.isWatched]
   * inside a write action. If the refresh in [OutsideConfigWatcher.watch] held a lock that `isWatched`
   * needed, a write action delivering other events would wait for that lock while the refresh waited for
   * the write action. This test holds a write action so the refresh stays waiting, and asks meanwhile.
   */
  fun testAskingWhileAWatchRefreshesDoesNotWait() {
    val file = createFile("a.json")
    val (watch, answer) = WriteAction.compute<Pair<Watching, Boolean?>, Throwable> {
      val watch = watchInBackground(file)
      waitUntilRefreshing(watch)
      val asked = CompletableFuture.supplyAsync { watcher.isWatched(OxcTool.OXLINT, file) }
      val answer = try {
        asked.get(5, TimeUnit.SECONDS)
      }
      catch (_: TimeoutException) {
        null
      }
      watch to answer
    }
    PlatformTestUtil.waitForFuture(watch.done, 30_000)

    assertEquals("isWatched should answer while the watch is refreshing", false, answer)
    assertTrue(watcher.isWatched(OxcTool.OXLINT, file))
  }

  private fun createFile(name: String): String =
    Files.writeString(folder.resolve(name), "{}").toString()

  private class Watching(val thread: AtomicReference<Thread?>, val done: Future<*>)

  private fun watchInBackground(file: String): Watching {
    val thread = AtomicReference<Thread?>()
    val done = ApplicationManager.getApplication().executeOnPooledThread {
      thread.set(Thread.currentThread())
      watcher.watch(OxcTool.OXLINT, file)
    }
    return Watching(thread, done)
  }

  /** Waits until the watch is blocked in its refresh. */
  private fun waitUntilRefreshing(watch: Watching) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
      val thread = watch.thread.get()
      // It can't leave the refresh while the test holds the write action.
      if (thread != null && thread.state != Thread.State.RUNNABLE &&
          thread.stackTrace.any { it.methodName == "refreshAndFindFileByPath" }) return
      Thread.sleep(10)
    }
    throw AssertionError("The watch never stayed in its refresh, so this test can't check anything")
  }
}
