package com.github.oxc.project.oxcintellijplugin.backend.process

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Turns a path from the settings into one the IDE can open. In a project in WSL or a container, a path
 * spelled as the project's environment spells it, such as `/workspaces/app/oxlint`, becomes the IDE's
 * path to it. Any other path is returned as it is, including a relative one and one on the IDE's side,
 * such as `\\wsl.localhost\Ubuntu\…` or `C:\…`. This only computes the path, so it never connects to the
 * environment.
 */
fun resolveSettingPath(project: Project, path: String): String =
  resolveSettingPath(project.getEelDescriptor(), path)

/**
 * The [projectEnvironment] wins over the IDE's machine for an absolute path, because the IDE's machine
 * has nothing the project's tools could run or read, and `GeneralCommandLine` runs such an executable in
 * the project's environment too.
 */
fun resolveSettingPath(
  projectEnvironment: EelDescriptor,
  path: String,
  environmentOf: (String) -> EelDescriptor = ::environmentOf,
): String {
  if (projectEnvironment is LocalEelDescriptor || environmentOf(path) !is LocalEelDescriptor) return path

  return try {
    FileUtil.toSystemIndependentName(EelPath.parse(path, projectEnvironment).asNioPath().toString())
  }
  // Not an absolute path in the project's environment.
  catch (_: EelPathException) {
    path
  }
  catch (_: IllegalArgumentException) {
    path
  }
}

/**
 * Where `GeneralCommandLine` runs an [executable], following its rules: the executable's environment, and
 * otherwise the [workingDirectory]'s, except that a Windows drive path always runs on the IDE's machine.
 */
fun launchEnvironment(
  executable: String,
  workingDirectory: String,
  isWindows: Boolean = SystemInfo.isWindows,
  environmentOf: (String) -> EelDescriptor = ::environmentOf,
): EelDescriptor {
  val executableEnvironment = environmentOf(executable)
  return when {
    executableEnvironment !is LocalEelDescriptor -> executableEnvironment
    isWindows && OSAgnosticPathUtil.isAbsoluteDosPath(executable) -> LocalEelDescriptor
    else -> environmentOf(workingDirectory)
  }
}

/** Only looks at the path, so it never connects to the environment. */
private fun environmentOf(path: String): EelDescriptor =
  try {
    Path.of(path).getEelDescriptor()
  }
  catch (_: InvalidPathException) {
    LocalEelDescriptor
  }
