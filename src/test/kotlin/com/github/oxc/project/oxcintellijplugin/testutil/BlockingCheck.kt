package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import jdk.jfr.Event
import jdk.jfr.Name
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordingFile
import kotlin.io.path.deleteIfExists

/** Starting a process, or a thread waiting on anything. These cover `wsl.exe` and blocking on a coroutine or a future. */
private val BLOCKING_EVENTS = listOf(
  "jdk.ProcessStart",
  "jdk.ThreadPark",
  "jdk.ThreadSleep",
  "jdk.JavaMonitorWait",
  "jdk.JavaMonitorEnter",
)

/** Marks where the checked code starts and ends in the recording. */
@Name("oxc.test.BlockingCheckBoundary")
class BlockingCheckBoundary : Event()

/**
 * Fails if [block] starts a process or makes the current thread wait, using the JVM's own flight
 * recorder so that it also sees what the platform does. It doesn't see a direct read of a file.
 * Class loading is ignored, because the first use of a class can wait for another thread to load it.
 */
fun <T> assertDoesNotBlock(block: () -> T): T {
  val folder = Files.createDirectories(Path.of(PathManager.getTempPath()))
  val file = Files.createTempFile(folder, "blocking-check", ".jfr")
  try {
    val result: T
    Recording().use { recording ->
      recording.enable(BlockingCheckBoundary::class.java)
      BLOCKING_EVENTS.forEach { recording.enable(it).withThreshold(Duration.ZERO).withStackTrace() }
      recording.start()
      BlockingCheckBoundary().commit()
      result = block()
      BlockingCheckBoundary().commit()
      recording.stop()
      recording.dump(file)
    }
    val blockingEvents = blockingEventsOnThisThread(RecordingFile.readAllEvents(file))
    check(blockingEvents.isEmpty()) {
      "Expected no blocking, but found:\n" + blockingEvents.joinToString("\n\n")
    }
    return result
  }
  finally {
    file.deleteIfExists()
  }
}

private fun blockingEventsOnThisThread(events: List<RecordedEvent>): List<RecordedEvent> {
  val thisThread = Thread.currentThread().threadId()
  val eventsOnThisThread = events.filter { it.thread?.javaThreadId == thisThread }
  val (boundaries, others) = eventsOnThisThread.partition { it.eventType.name == "oxc.test.BlockingCheckBoundary" }
  val times = boundaries.map { it.startTime }
  return others.filter { it.startTime in times.min()..times.max() && !it.isClassLoading() }
}

private fun RecordedEvent.isClassLoading(): Boolean =
  stackTrace?.frames.orEmpty().any {
    val className = it.method.type.name
    className == "java.lang.ClassLoader" ||
    className.startsWith("jdk.internal.loader.") ||
    className.startsWith("com.intellij.util.lang.")
  }
