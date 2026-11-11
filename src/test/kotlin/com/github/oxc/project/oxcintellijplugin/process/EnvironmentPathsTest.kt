package com.github.oxc.project.oxcintellijplugin.process

import com.github.oxc.project.oxcintellijplugin.backend.process.EelServerPathMapper
import com.github.oxc.project.oxcintellijplugin.backend.process.launchEnvironment
import com.github.oxc.project.oxcintellijplugin.backend.process.resolveSettingPath
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolConfigPath
import com.github.oxc.project.oxcintellijplugin.testutil.assertDoesNotBlock
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import junit.framework.TestCase
import java.nio.file.Path

/**
 * A test has no container, so [container] stands in for one. Its root is where the IDE reaches it, as
 * the platform's Dev Containers are under `/$devcontainer.ij/`.
 */
class EnvironmentPathsTest : TestCase() {

  private val containerRoot = "/\$devcontainer.ij/app@"

  private val container = object : EelPathBoundDescriptor {
    override val rootPath: Path = Path.of(containerRoot)
    override val name: String = "Test container"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }

  /** Which environment a path is in, as the platform would tell for [container]. */
  private fun environmentOf(path: String): EelDescriptor =
    if (FileUtil.toSystemIndependentName(path).startsWith("$containerRoot/")) container else LocalEelDescriptor

  /** The platform's `asEelPath`, for [container]. */
  private fun toEelPath(path: Path): EelPath {
    val local = FileUtil.toSystemIndependentName(path.toString())
    return if (environmentOf(local) == container) EelPath.parse(local.removePrefix(containerRoot), container)
    else EelPath.parse(path.toString(), LocalEelDescriptor)
  }

  private val mapper = EelServerPathMapper(container, ::toEelPath)

  fun testFileInTheContainerUsesItsPathThere() {
    assertEquals("/workspaces/app/a.ts", mapper.toServerPath("$containerRoot/workspaces/app/a.ts"))
  }

  fun testFileOnTheIdesMachineIsNotReachable() {
    assertNull(mapper.toServerPath(Path.of("/Users/me/a.ts").toAbsolutePath().toString()))
  }

  fun testRelativePathIsNotReachable() {
    assertNull(mapper.toServerPath("a.ts"))
  }

  fun testServerPathIsUnderTheContainersRoot() {
    assertEquals("$containerRoot/workspaces/app/a.ts", mapper.toLocalPath("/workspaces/app/a.ts"))
  }

  /** A config path from the settings is already the IDE's, and mapping it again would prefix the root twice. */
  fun testIdesPathIntoTheContainerIsNotAServerPath() {
    assertNull(mapper.toLocalPath("$containerRoot/opt/oxlint-config.json"))
  }

  /** What `ToolConfigPath` does with a config outside the workspace, which a real Dev Container got wrong. */
  fun testConfigOutsideTheWorkspaceIsSentAsTheContainersPath() {
    val configured = resolveSettingPath(container, "/opt/oxlint-config.json", ::environmentOf)

    val config = ToolConfigPath.of(configured, "$containerRoot/IdeaProjects/project", mapper::toServerPath, mapper::toLocalPath)

    assertEquals(ToolConfigPath("/opt/oxlint-config.json", "$containerRoot/opt/oxlint-config.json"), config)
  }

  fun testRelativeServerPathHasNoLocalPath() {
    assertNull(mapper.toLocalPath("workspaces/app/a.ts"))
  }

  fun testPathsRoundTrip() {
    val local = "$containerRoot/workspaces/app/src/a b#c.ts"

    assertEquals(local, mapper.toLocalPath(mapper.toServerPath(local)!!))
  }

  /** The LSP client converts paths on the UI thread. */
  fun testConversionsDoNotBlock() {
    assertDoesNotBlock {
      mapper.toServerPath("$containerRoot/workspaces/app/a.ts")
      mapper.toLocalPath("/workspaces/app/a.ts")
    }
  }

  fun testSettingInALocalProjectIsUnchanged() {
    assertEquals("/workspaces/app/oxlint", resolveSettingPath(LocalEelDescriptor, "/workspaces/app/oxlint", ::environmentOf))
  }

  fun testSettingSpelledAsTheContainerSpellsItIsFound() {
    assertEquals(
      "$containerRoot/workspaces/app/node_modules/.bin/oxlint",
      resolveSettingPath(container, "/workspaces/app/node_modules/.bin/oxlint", ::environmentOf),
    )
  }

  fun testSettingAlreadyInTheContainerIsUnchanged() {
    val path = "$containerRoot/workspaces/app/oxlint"

    assertEquals(path, resolveSettingPath(container, path, ::environmentOf))
  }

  fun testRelativeSettingIsUnchanged() {
    assertEquals("config/.oxlintrc.json", resolveSettingPath(container, "config/.oxlintrc.json", ::environmentOf))
  }

  /** A Linux environment has no drives, so a drive path can only be on the IDE's side. */
  fun testWindowsSettingInALinuxEnvironmentIsUnchanged() {
    assertEquals("C:/tools/oxlint.exe", resolveSettingPath(container, "C:/tools/oxlint.exe", ::environmentOf))
  }

  fun testExecutableInTheContainerRunsThere() {
    assertEquals(container, launchEnvironment("$containerRoot/bin/oxlint", "/project", false, ::environmentOf))
  }

  fun testCommandRunsWhereTheWorkingDirectoryIs() {
    assertEquals(container, launchEnvironment("oxlint", "$containerRoot/workspaces/app", false, ::environmentOf))
  }

  fun testLocalExecutableAndFolderRunLocally() {
    assertEquals(LocalEelDescriptor, launchEnvironment("/bin/oxlint", "/project", false, ::environmentOf))
  }

  /** `GeneralCommandLine` doesn't use Eel for a Windows drive path, whatever the working directory. */
  fun testWindowsDrivePathRunsLocally() {
    assertEquals(
      LocalEelDescriptor,
      launchEnvironment("C:/tools/oxlint.exe", "$containerRoot/workspaces/app", true, ::environmentOf),
    )
  }
}
