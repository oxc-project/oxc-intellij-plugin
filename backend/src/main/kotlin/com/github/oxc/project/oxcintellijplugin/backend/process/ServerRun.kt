package com.github.oxc.project.oxcintellijplugin.backend.process

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable

private val LOG = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin")

private const val NODE_SHEBANG = "#!/usr/bin/env node"

/** Starts a language server, and converts paths between the IDE and the machine it runs on. */
interface ServerRun : ServerPathMapper {

  /**
   * Starts the server. This runs on a background thread before the LSP client sends the server
   * anything.
   */
  fun startProcess(): OSProcessHandler
}

/**
 * Whether a file has to run through Node. A relative [executable] is relative to the server's
 * [workingDirectory]. Anything that can't be read counts as not being a Node script, which includes
 * a command that's found through `PATH`.
 */
fun isNodeScript(executable: String, workingDirectory: String): Boolean =
  try {
    // Not resolved if absolute, because a working directory in WSL would read a Windows path as a relative name.
    val path = Path.of(executable).takeIf(Path::isAbsolute) ?: Path.of(workingDirectory).resolve(executable)
    Files.newInputStream(path).use {
      String(it.readNBytes(NODE_SHEBANG.length), Charsets.US_ASCII) == NODE_SHEBANG
    }
  }
  catch (_: IOException) {
    false
  }
  catch (_: InvalidPathException) {
    false
  }

/** The log level for the server, for any of its variables that aren't set already. */
fun logEnvironment(existingVariables: Set<String>): Map<String, String> {
  val level = when {
    LOG.isTraceEnabled -> "TRACE"
    LOG.isDebugEnabled -> "DEBUG"
    else -> "INFO"
  }
  return listOf("OXC_LOG", "RUST_LOG").filterNot(existingVariables::contains)
    .associateWith { level }
}

/**
 * Runs [start] with a progress indicator, without showing any progress. The platform calls the
 * server start on a background thread that may have no job, so this blocks the way the platform's
 * own server start does.
 */
internal fun startWithoutProgress(start: () -> OSProcessHandler): OSProcessHandler =
  runBlockingMaybeCancellable { coroutineToIndicator { start() } }
