package com.github.oxc.project.oxcintellijplugin.backend.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import java.nio.file.Path

/**
 * Runs a language server's executable directly. It runs on the backend's machine, or inside WSL if
 * [executable] is a path into a distribution, and the project's Node interpreter isn't involved.
 */
class NativeServerRun(
  private val executable: String,
  private val parameters: List<String>,
  private val workingDirectory: String,
) : ServerRun {

  @Volatile
  private var pathMapper: ServerPathMapper? = null

  override fun startProcess(): OSProcessHandler = startWithoutProgress {
    val commandLine = nativeCommandLine(executable, parameters, workingDirectory)
    val mapper = patchForWsl(commandLine)
    val processHandler = NativeProcessHandler(commandLine)
    pathMapper = mapper
    processHandler
  }

  override fun toServerPath(localPath: String): String? =
    pathMapper?.toServerPath(localPath)

  override fun toLocalPath(serverPath: String): String? =
    pathMapper?.toLocalPath(serverPath)

  /** Returns how paths convert for wherever the server will run. */
  private fun patchForWsl(commandLine: GeneralCommandLine): ServerPathMapper {
    val distribution = WslPath.getDistributionByWindowsUncPath(executable) ?: return UnchangedPathMapper

    val mapper = createWslPathMapper(distribution, workingDirectory)
    commandLine.withExePath(checkNotNull(mapper.toServerPath(executable)))
    val options = WSLCommandLineOptions()
      .setPassEnvVarsUsingInterop(true)
      .setRemoteWorkingDirectory(mapper.toServerPath(workingDirectory))
    distribution.patchCommandLine(commandLine, null, options)
    return mapper
  }
}

/**
 * Builds the command line for a local executable. Windows can only start a `.cmd` or `.bat` file
 * through its command interpreter.
 */
fun nativeCommandLine(
  executable: String,
  parameters: List<String>,
  workingDirectory: String,
  isWindows: Boolean = SystemInfo.isWindows,
  commandInterpreter: String? = System.getenv("ComSpec"),
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
  return commandLine.withEnvironment(logEnvironment(commandLine.effectiveEnvironment.keys))
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
