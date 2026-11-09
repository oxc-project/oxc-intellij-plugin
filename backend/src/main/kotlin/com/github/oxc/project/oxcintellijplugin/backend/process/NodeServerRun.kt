package com.github.oxc.project.oxcintellijplugin.backend.process

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.NodeTargetRunOptions
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor

/**
 * Launches a language server through the project's Node interpreter, and converts paths between the
 * IDE and the machine that the interpreter runs on.
 */
class NodeServerRun(
  private val project: Project,
  private val executable: String,
  private val parameters: List<String>,
  private val workingDirectory: String,
) : ServerRun {

  private val run: NodeTargetRun by lazy { createRun() }

  @Volatile
  private var pathMapper: ServerPathMapper? = null

  override fun startProcess(): OSProcessHandler = startWithoutProgress {
    val wslPathMapper = (run.interpreter as? WslNodeInterpreter)?.distribution
      ?.let { createWslPathMapper(it, workingDirectory) }
    run.envData = run.envData.with(logEnvironment(run.envData.envs.keys))
    val processHandler = run.startProcessEx().processHandler
    pathMapper = wslPathMapper ?: nodePathMapper(run.interpreter, project.getEelDescriptor(), TargetRunPathMapper(run))
    processHandler
  }

  /** Doesn't block. Returns null if the server hasn't started or can't reach the file. */
  override fun toServerPath(localPath: String): String? = pathMapper?.toServerPath(localPath)

  /** Doesn't block. Returns null if the server hasn't started or the file has no local path. */
  override fun toLocalPath(serverPath: String): String? = pathMapper?.toLocalPath(serverPath)

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
}
