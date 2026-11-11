package com.github.oxc.project.oxcintellijplugin.backend.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import java.nio.file.Path

private val LOG = Logger.getInstance(NativeServerRun::class.java)

/**
 * Runs a language server's executable directly, and the project's Node interpreter isn't involved. It runs
 * on the backend's machine.
 */
class NativeServerRun(
  private val executable: String,
  private val parameters: List<String>,
  private val workingDirectory: String,
) : ServerRun {

  @Volatile
  private var pathMapper: ServerPathMapper? = null

  override fun startProcess(): OSProcessHandler = startWithoutProgress {
    val commandLine = nativeCommandLine(
      executable, parameters, workingDirectory, isWindows = LocalEelDescriptor.osFamily.isWindows)
    val processHandler = NativeProcessHandler(commandLine)
    pathMapper = UnchangedPathMapper
    processHandler
  }

  override fun toServerPath(localPath: String): String? =
    pathMapper?.toServerPath(localPath)

  override fun toLocalPath(serverPath: String): String? =
    pathMapper?.toLocalPath(serverPath)

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
  isWindows: Boolean,
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
