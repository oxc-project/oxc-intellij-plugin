package com.github.oxc.project.oxcintellijplugin.backend.process

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.NodeTargetRunOptions
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable

private val LOG = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin")

/**
 * Launches a language server through the project's Node interpreter, and converts paths between the
 * IDE and the machine that the interpreter runs on.
 */
class NodeServerRun(
  private val project: Project,
  private val executable: String,
  private val parameters: List<String>,
  private val workingDirectory: String,
) {

  private val run: NodeTargetRun by lazy { createRun() }

  @Volatile
  private var pathMapper: ServerPathMapper? = null

  /**
   * Starts the server. This runs on a background thread before the LSP client sends the server
   * anything, so it's where the work that can block is done on behalf of [toServerPath] and [toLocalPath].
   */
  fun startProcess(): OSProcessHandler =
    // TODO: This is marked as obsolete. Figure out the alternative.
    ProgressManager.getInstance().runProcess(
      Computable {
        val wslPathMapper = createWslPathMapper(run.interpreter)
        run.envData = run.envData.with(logEnvironment(run.envData.envs.keys))
        val processHandler = run.startProcessEx().processHandler
        pathMapper = wslPathMapper ?: TargetRunPathMapper(run)
        processHandler
      },
      EmptyProgressIndicator(),
    )

  /** Doesn't block. Returns null if the server hasn't started or can't reach the file. */
  fun toServerPath(localPath: String): String? = pathMapper?.toServerPath(localPath)

  /** Doesn't block. Returns null if the server hasn't started or the file has no local path. */
  fun toLocalPath(serverPath: String): String? = pathMapper?.toLocalPath(serverPath)

  private fun createWslPathMapper(interpreter: NodeJsInterpreter): ServerPathMapper? {
    val distribution = (interpreter as? WslNodeInterpreter)?.distribution ?: return null
    val mapper = WslServerPathMapper(distribution, distribution.mntRoot, workingDirectory)
    mapper.toServerPath(workingDirectory)
    ?: throw ExecutionException(
      OxcBackendBundle.message("wsl.folder.not.reachable", workingDirectory, distribution.msId))
    return mapper
  }

  private fun createRun(): NodeTargetRun {
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
                      ?: throw ExecutionException(
                        OxcBackendBundle.message("node.interpreter.not.configured"))

    val targetRun = NodeTargetRun(interpreter, project, null, NodeTargetRunOptions.of(false))
    targetRun.commandLineBuilder.apply {
      setWorkingDirectory(targetRun.path(workingDirectory))
      addParameter(targetRun.path(executable))
      parameters.forEach(::addParameter)
      charset = Charsets.UTF_8
    }
    return targetRun
  }

  private fun logEnvironment(existingVariables: Set<String>): Map<String, String> {
    val level = when {
      LOG.isTraceEnabled -> "TRACE"
      LOG.isDebugEnabled -> "DEBUG"
      else -> "INFO"
    }
    return listOf("OXC_LOG", "RUST_LOG").filterNot(existingVariables::contains)
      .associateWith { level }
  }
}
