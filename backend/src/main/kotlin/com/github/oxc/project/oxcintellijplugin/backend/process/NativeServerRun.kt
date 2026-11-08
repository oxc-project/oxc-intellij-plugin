package com.github.oxc.project.oxcintellijplugin.backend.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import java.nio.file.Path

private val LOG = Logger.getInstance(NativeServerRun::class.java)

/**
 * Runs a language server's executable directly, and the project's Node interpreter isn't involved. It runs
 * on the backend's machine, inside WSL if [executable] is a path into a distribution, or in the container or
 * other environment that [executable] or [workingDirectory] is in, which `GeneralCommandLine` reaches
 * through Eel.
 */
class NativeServerRun(
  private val executable: String,
  private val parameters: List<String>,
  private val workingDirectory: String,
) : ServerRun {

  @Volatile
  private var pathMapper: ServerPathMapper? = null

  override fun startProcess(): OSProcessHandler = startWithoutProgress {
    val distribution = WslPath.getDistributionByWindowsUncPath(executable)
    val (commandLine, mapper) = if (distribution != null) wslCommandLine(distribution) else environmentCommandLine()
    val processHandler = NativeProcessHandler(commandLine)
    pathMapper = mapper
    processHandler
  }

  override fun toServerPath(localPath: String): String? =
    pathMapper?.toServerPath(localPath)

  override fun toLocalPath(serverPath: String): String? =
    pathMapper?.toLocalPath(serverPath)

  /**
   * WSL keeps its own command line, because its paths map a project on the Windows side to `/mnt/c`,
   * which Eel can't do. The platform runs the patched command line through Eel where it can.
   */
  private fun wslCommandLine(distribution: WSLDistribution): Pair<GeneralCommandLine, ServerPathMapper> {
    val commandLine = nativeCommandLine(executable, parameters, workingDirectory, isWindows = false)
    val mapper = createWslPathMapper(distribution, workingDirectory)
    commandLine.withExePath(checkNotNull(mapper.toServerPath(executable)))
    val options = WSLCommandLineOptions()
      .setPassEnvVarsUsingInterop(true)
      .setRemoteWorkingDirectory(mapper.toServerPath(workingDirectory))
    distribution.patchCommandLine(commandLine, null, options)
    return commandLine to mapper
  }

  /** The IDE's machine or another environment, which `GeneralCommandLine` picks the same way. */
  private fun environmentCommandLine(): Pair<GeneralCommandLine, ServerPathMapper> {
    val environment = launchEnvironment(executable, workingDirectory)
    if (environment is LocalEelDescriptor) {
      return nativeCommandLine(executable, parameters, workingDirectory) to UnchangedPathMapper
    }

    val commandLine = nativeCommandLine(
      executable, parameters, workingDirectory,
      isWindows = environment.osFamily.isWindows,
      commandInterpreter = null,
      existingVariables = environmentVariables(environment),
    )
    return commandLine to EelServerPathMapper(environment)
  }
}

/**
 * The names of the variables that a process in [environment] gets, which are its own and not the IDE's.
 * Connects to the environment, which starting the server does anyway. If that fails, starting the server
 * reports why.
 */
private fun environmentVariables(environment: EelDescriptor): Set<String> =
  try {
    runBlockingMaybeCancellable {
      environment.toEelApi().exec.environmentVariables().eelIt().await().keys
    }
  }
  catch (exception: EelExecApi.EnvironmentVariablesException) {
    LOG.warn("Couldn't read the variables in $environment", exception)
    emptySet()
  }
  catch (exception: EelUnavailableException) {
    LOG.warn("Couldn't reach $environment", exception)
    emptySet()
  }

/**
 * Builds the command line for an executable. Windows can only start a `.cmd` or `.bat` file through its
 * command interpreter. [isWindows] is about the machine that runs it, and [existingVariables] are the
 * variables it already has there, which default to the IDE's.
 */
fun nativeCommandLine(
  executable: String,
  parameters: List<String>,
  workingDirectory: String,
  isWindows: Boolean = SystemInfo.isWindows,
  commandInterpreter: String? = System.getenv("ComSpec"),
  existingVariables: Set<String>? = null,
): GeneralCommandLine {
  val commandLine = GeneralCommandLine()
    .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    .withWorkingDirectory(Path.of(workingDirectory))
    .withCharset(Charsets.UTF_8)

  if (isWindows && (executable.endsWith(".cmd", ignoreCase = true) || executable.endsWith(".bat", ignoreCase = true))) {
    commandLine.withExePath(commandInterpreter ?: "cmd.exe")
    commandLine.addParameters("/d", "/c", GeneralCommandLine.inescapableQuote(executable))
  }
  else {
    commandLine.withExePath(executable)
  }
  commandLine.addParameters(parameters)
  return commandLine.withEnvironment(logEnvironment(existingVariables ?: commandLine.effectiveEnvironment.keys))
}

/** A server on the same machine as the IDE sees the same paths. */
private object UnchangedPathMapper : ServerPathMapper {

  override fun toServerPath(localPath: String): String = localPath

  override fun toLocalPath(serverPath: String): String = serverPath
}

/** Reads output the same way as the platform's own LSP process, because LSP's framing needs the raw bytes. */
private class NativeProcessHandler(commandLine: GeneralCommandLine) : OSProcessHandler(commandLine) {

  override fun readerOptions(): BaseOutputReader.Options = object : BaseOutputReader.Options() {
    override fun policy(): BaseDataReader.SleepingPolicy = forMostlySilentProcess().policy()

    override fun splitToLines(): Boolean = false
  }
}
