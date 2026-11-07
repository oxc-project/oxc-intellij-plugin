package com.github.oxc.project.oxcintellijplugin.backend.process

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private val LOG = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin")

private const val NODE_SHEBANG = "#!/usr/bin/env node"

/** Starts a language server, and converts paths between the IDE and the machine it runs on. */
interface ServerRun : ServerPathMapper {

  /**
   * Starts the server. This runs on a background thread before the LSP client sends the server
   * anything, so it's where the work that can block is done on behalf of the path conversions.
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
    Files.newInputStream(Path.of(workingDirectory).resolve(executable)).use {
      String(it.readNBytes(NODE_SHEBANG.length), Charsets.US_ASCII) == NODE_SHEBANG
    }
  }
  catch (_: IOException) {
    false
  }
  catch (_: InvalidPathException) {
    false
  }

internal fun createWslPathMapper(distribution: WSLDistribution, workingDirectory: String): ServerPathMapper {
  val mapper = WslServerPathMapper(distribution, distribution.mntRoot, workingDirectory)
  mapper.toServerPath(workingDirectory)
  ?: throw ExecutionException(
    OxcBackendBundle.message("wsl.folder.not.reachable", workingDirectory, distribution.msId))
  return mapper
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

internal fun startWithoutProgress(start: () -> OSProcessHandler): OSProcessHandler =
  // TODO: This is marked as obsolete. Figure out the alternative.
  ProgressManager.getInstance().runProcess(Computable(start), EmptyProgressIndicator())
