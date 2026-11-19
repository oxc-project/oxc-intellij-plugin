package com.github.oxc.project.oxcintellijplugin.testutil

import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.openapi.application.PathManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.util.ThrowableRunnable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * Names in the command of a process that runs a server: Node and the native binaries. Not WSL's own
 * `wsl.exe` and `wslhost.exe`: a server in WSL runs through IJent as a Linux process, so it isn't a Windows
 * child of the test, and the `wsl.exe` chain that IJent starts on first use lives for the whole session.
 * Windows hides a process's arguments, so that chain can't be told apart from anything else by name.
 */
private val SERVER_COMMANDS = listOf("node", "oxlint", "oxfmt")

/**
 * In every server's command line. The platform names the thread that waits for a process after its command
 * line until it has closed the process's streams, which in WSL goes through IJent and can outlast the
 * process.
 */
private const val SERVER_ARGUMENT = "--lsp"

/**
 * Base class for tests that use the project's Node interpreter. When [WslTesting] is enabled, the
 * interpreter is the WSL distribution's Node.js, so the same tests also cover a server running in WSL.
 */
abstract class OxcFixtureTestCase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private var processesBefore: Set<Long> = emptySet()
  private var started: Instant = Instant.now()
  private var failed = false

  override fun setUp() {
    started = Instant.now()
    super.setUp()
    // Before the snapshot, so that the wsl.exe chain IJent keeps for the session counts as already running.
    if (WslTesting.enabled) WslTesting.warmUp()
    processesBefore = ProcessHandle.current().descendants().map { it.pid() }.toList().toSet()
    if (WslTesting.enabled) {
      NodeJsInterpreterManager.getInstance(project)
        .setInterpreterRef(WslTesting.nodeInterpreterRef(), testRootDisposable)
    }
  }

  /**
   * Waits for the test's servers to exit before its folders are deleted. A server runs in its root, inside
   * the test's folder, and Windows can't delete the working directory of a running process. A stop from
   * the UI thread, such as Fix All's for a closed file, returns before the process has exited.
   *
   * Then waits for the platform's threads for those servers, which the leak check after the test would
   * report. A server in WSL isn't a Windows process, so for it this is the only wait.
   */
  override fun tearDown() {
    try {
      for (definition in ToolDefinition.all) {
        LspClientManager.getInstance(project).stopClients(definition.providerClass)
      }
      try {
        waitUntil("the test's server processes have exited") { serverProcesses().isEmpty() }
      }
      catch (e: AssertionError) {
        throw AssertionError("${e.message}. Still running: ${serverProcesses().map(::details)}", e)
      }
      try {
        waitUntil("the threads for the test's servers have finished") { serverThreads().isEmpty() }
      }
      catch (e: AssertionError) {
        throw AssertionError("${e.message}. Still running: ${serverThreads()}", e)
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      try {
        if (failed) keepServerLogs()
      }
      catch (e: Throwable) {
        addSuppressedException(e)
      }
      finally {
        super.tearDown()
      }
    }
  }

  override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
    try {
      super.runTestRunnable(testRunnable)
    }
    catch (e: Throwable) {
      failed = true
      throw e
    }
  }

  /**
   * Copies the server logs that a failed test wrote to `failed-tests` beside them, which CI uploads. The
   * platform keeps only the newest 10 in `language-services`, so a whole run's later tests delete them.
   * Runs after the servers have stopped, so a log ends with how its server exited.
   */
  private fun keepServerLogs() {
    val logs = Path.of(PathManager.getLogPath())
    val source = logs.resolve("language-services")
    if (!Files.isDirectory(source)) return
    val target = logs.resolve("failed-tests").resolve("${javaClass.simpleName}.$name")
    Files.list(source).use { files ->
      files.filter { Files.getLastModifiedTime(it).toInstant() >= started }.forEach { log ->
        Files.createDirectories(target)
        Files.copy(log, target.resolve(log.fileName), StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  /** On Windows only the command is known, without its arguments, so this goes by the command's name. */
  private fun serverProcesses(): List<ProcessHandle> =
    ProcessHandle.current().descendants()
      .filter { it.isAlive && it.pid() !in processesBefore }
      .filter { process -> describe(process).lowercase().let { command -> SERVER_COMMANDS.any { it in command } } }
      .toList()

  private fun serverThreads(): List<String> =
    Thread.getAllStackTraces().keys.filter { it.isAlive && SERVER_ARGUMENT in it.name }.map { it.name }

  private fun describe(process: ProcessHandle): String =
    process.info().commandLine().or { process.info().command() }.orElse("")

  /** Enough to tell the processes left at a timeout apart in a CI log. */
  private fun details(process: ProcessHandle): String {
    val parent = process.parent().map { it.pid().toString() }.orElse("none")
    val started = process.info().startInstant().map { it.toString() }.orElse("unknown")
    return "${describe(process)} (pid ${process.pid()}, parent $parent, started $started)"
  }
}
