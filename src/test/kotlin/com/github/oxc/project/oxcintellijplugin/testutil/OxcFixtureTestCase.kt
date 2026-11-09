package com.github.oxc.project.oxcintellijplugin.testutil

import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

/** Names in the command of a process that runs a server: Node, the native binaries and WSL. */
private val SERVER_COMMANDS = listOf("node", "oxlint", "oxfmt", "wsl")

/**
 * Base class for tests that use the project's Node interpreter. When [WslTesting] is enabled, the
 * interpreter is the WSL distribution's Node.js, so the same tests also cover a server running in WSL.
 */
abstract class OxcFixtureTestCase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private var processesBefore: Set<Long> = emptySet()

  override fun setUp() {
    super.setUp()
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
        throw AssertionError("${e.message}. Still running: ${serverProcesses().map(::describe)}", e)
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  /** On Windows only the command is known, without its arguments, so this goes by the command's name. */
  private fun serverProcesses(): List<ProcessHandle> =
    ProcessHandle.current().descendants()
      .filter { it.isAlive && it.pid() !in processesBefore }
      .filter { process -> describe(process).lowercase().let { command -> SERVER_COMMANDS.any { it in command } } }
      .toList()

  private fun describe(process: ProcessHandle): String =
    process.info().commandLine().or { process.info().command() }.orElse("")
}
