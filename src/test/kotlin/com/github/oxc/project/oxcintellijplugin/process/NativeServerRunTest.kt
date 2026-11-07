package com.github.oxc.project.oxcintellijplugin.process

import com.github.oxc.project.oxcintellijplugin.backend.process.isNodeScript
import com.github.oxc.project.oxcintellijplugin.backend.process.logEnvironment
import com.github.oxc.project.oxcintellijplugin.backend.process.nativeCommandLine
import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import java.nio.file.Files
import java.nio.file.Path

class NativeServerRunTest : TestCase() {

  private lateinit var folder: Path

  override fun setUp() {
    super.setUp()
    folder = FileUtil.createTempDirectory("oxc-native", null, true).toPath()
  }

  fun testNodeShebangIsANodeScript() {
    assertTrue(isNodeScript(write("oxlint", "#!/usr/bin/env node\nconsole.log()\n"), folder.toString()))
  }

  fun testOtherShebangIsNotANodeScript() {
    assertFalse(isNodeScript(write("oxlint", "#!/bin/sh\nexec oxlint\n"), folder.toString()))
  }

  fun testBinaryFileIsNotANodeScript() {
    val file = folder.resolve("oxlint")
    Files.write(file, byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0, -1, -2, 10))

    assertFalse(isNodeScript(file.toString(), folder.toString()))
  }

  fun testEmptyFileIsNotANodeScript() {
    assertFalse(isNodeScript(write("oxlint", ""), folder.toString()))
  }

  fun testMissingFileIsNotANodeScript() {
    assertFalse(isNodeScript(folder.resolve("missing").toString(), folder.toString()))
  }

  fun testFolderIsNotANodeScript() {
    assertFalse(isNodeScript(folder.toString(), folder.toString()))
  }

  fun testRelativePathIsReadFromTheWorkingDirectory() {
    write("oxlint", "#!/usr/bin/env node\n")

    assertTrue(isNodeScript("oxlint", folder.toString()))
  }

  // In WSL, the working directory's file system would read a Windows path as a relative name.
  fun testAbsolutePathIsReadWithoutTheWorkingDirectory() {
    assertTrue(isNodeScript(write("oxlint", "#!/usr/bin/env node\n"), "\u0000 can't be a path"))
  }

  fun testCommandOnThePathIsNotANodeScript() {
    assertFalse(isNodeScript("oxlint", folder.toString()))
  }

  fun testExecutableRunsDirectlyWithItsParameters() {
    val commandLine = nativeCommandLine("/bin/oxlint", listOf("--lsp"), folder.toString(), isWindows = false)

    assertEquals("/bin/oxlint", commandLine.exePath)
    assertEquals(listOf("--lsp"), commandLine.parametersList.list)
    assertEquals(folder, commandLine.workingDirectory)
    assertEquals(Charsets.UTF_8, commandLine.charset)
  }

  fun testWindowsExeRunsDirectly() {
    val commandLine = nativeCommandLine("C:/tools/oxlint.exe", listOf("--lsp"), folder.toString(), isWindows = true)

    assertEquals("C:/tools/oxlint.exe", commandLine.exePath)
    assertEquals(listOf("--lsp"), commandLine.parametersList.list)
  }

  fun testWindowsScriptsRunThroughTheCommandInterpreter() {
    for (name in listOf("oxlint.cmd", "oxlint.CMD", "oxlint.bat", "oxlint.Bat")) {
      val commandLine = nativeCommandLine(
        "C:/my tools/$name", listOf("--lsp"), folder.toString(), isWindows = true, commandInterpreter = "C:/cmd.exe")

      assertEquals("C:/cmd.exe", commandLine.exePath)
      assertEquals("/d", commandLine.parametersList.list[0])
      assertEquals("/c", commandLine.parametersList.list[1])
      assertTrue(commandLine.parametersList.list[2], commandLine.parametersList.list[2].contains("C:/my tools/$name"))
      assertEquals("--lsp", commandLine.parametersList.list[3])
    }
  }

  fun testCommandInterpreterDefaultsToCmd() {
    val commandLine = nativeCommandLine(
      "oxlint.cmd", emptyList(), folder.toString(), isWindows = true, commandInterpreter = null)

    assertEquals("cmd.exe", commandLine.exePath)
  }

  fun testScriptExtensionsMeanNothingOutsideWindows() {
    val commandLine = nativeCommandLine("/bin/oxlint.cmd", emptyList(), folder.toString(), isWindows = false)

    assertEquals("/bin/oxlint.cmd", commandLine.exePath)
  }

  fun testLogLevelIsOnlySetForVariablesThatAreNotSet() {
    assertEquals(setOf("OXC_LOG", "RUST_LOG"), logEnvironment(emptySet()).keys)
    assertEquals(setOf("RUST_LOG"), logEnvironment(setOf("OXC_LOG")).keys)
    assertEmpty(logEnvironment(setOf("OXC_LOG", "RUST_LOG")).keys)
  }

  private fun assertEmpty(values: Collection<*>) = assertTrue(values.toString(), values.isEmpty())

  private fun write(name: String, content: String): String =
    Files.writeString(folder.resolve(name), content).toString()
}
