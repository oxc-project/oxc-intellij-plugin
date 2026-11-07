package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Settings for running the tests with a language server inside WSL, which is enabled on Windows when
 * `OXC_TEST_WSL_DISTRO` names an installed distribution with Node.js at `OXC_TEST_WSL_NODE`
 * (default `/usr/local/bin/node`). Fixtures need `npm ci` run inside WSL to install the Linux Oxlint
 * binary. See [OxcFixtureTestCase].
 */
object WslTesting {

  val distributionId: String? = System.getenv("OXC_TEST_WSL_DISTRO")?.takeIf { it.isNotBlank() }

  val nodePath: String =
    System.getenv("OXC_TEST_WSL_NODE")?.takeIf { it.isNotBlank() } ?: "/usr/local/bin/node"

  val enabled: Boolean
    get() = SystemInfo.isWindows && distributionId != null

  /** The distribution's Node.js, as the project interpreter would be set in the IDE. */
  fun nodeInterpreterRef(): NodeJsInterpreterRef =
    NodeJsInterpreterRef.create(WslNodeInterpreter(distributionId!!, nodePath))

  /** A local path as the distribution sees it. */
  fun toWslPath(localPath: String): String =
    onPooledThread { checkNotNull(WSLDistribution(distributionId!!).getWslPath(Path.of(localPath))) }

  /**
   * Runs [block] off the EDT. Starting a server may run `wsl.exe`, which the IDE forbids on the EDT
   * and does on background threads itself.
   */
  fun <T> onPooledThread(block: () -> T): T =
    ApplicationManager.getApplication().executeOnPooledThread(Callable(block)).get(60, TimeUnit.SECONDS)
}
