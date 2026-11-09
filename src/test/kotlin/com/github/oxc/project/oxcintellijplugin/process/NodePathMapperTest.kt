package com.github.oxc.project.oxcintellijplugin.process

import com.github.oxc.project.oxcintellijplugin.backend.process.EelServerPathMapper
import com.github.oxc.project.oxcintellijplugin.backend.process.ServerPathMapper
import com.github.oxc.project.oxcintellijplugin.backend.process.nodePathMapper
import com.github.oxc.project.oxcintellijplugin.testutil.assertDoesNotBlock
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterType
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.NullableConsumer
import com.intellij.util.text.SemVer
import junit.framework.TestCase
import java.nio.file.Path

/**
 * Which mapper a Node interpreter outside WSL gets. A Dev Container's own Node has a run with no path
 * mapper, whose conversion asks the container's file system whether a path is a folder, so its paths must
 * come from the environment. A test has no container, so [container] stands in for one, as in
 * [EnvironmentPathsTest].
 */
class NodePathMapperTest : TestCase() {

  private val containerRoot = "/\$devcontainer.ij/app@"

  private val container = object : EelPathBoundDescriptor {
    override val rootPath: Path = Path.of(containerRoot)
    override val name: String = "Test container"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }

  /** The platform's `asEelPath`, for [container]. */
  private fun toEelPath(path: Path): EelPath {
    val local = FileUtil.toSystemIndependentName(path.toString())
    return if (local.startsWith("$containerRoot/")) EelPath.parse(local.removePrefix(containerRoot), container)
    else EelPath.parse(path.toString(), LocalEelDescriptor)
  }

  /** Stands in for the run's conversion, recording what it was asked. */
  private class TargetRun : ServerPathMapper {
    val asked = mutableListOf<String>()
    override fun toServerPath(localPath: String): String = "target:$localPath".also { asked += localPath }
    override fun toLocalPath(serverPath: String): String = "local:$serverPath".also { asked += serverPath }
  }

  /** An interpreter that's neither local, Docker nor SSH, as the JavaScript plugin's Eel interpreter is. */
  private class EnvironmentInterpreter : NodeJsInterpreter() {
    override fun getType(): NodeJsInterpreterType<out NodeJsInterpreter> = throw UnsupportedOperationException()
    override fun getReferenceName(): String = "node in the container"
    override fun getPresentableName(): String = "node in the container"
    override fun getCachedVersion(): Ref<SemVer>? = null
    override fun fetchVersion(consumer: NullableConsumer<in SemVer>) = consumer.consume(null)
    override fun validate(project: Project?): String? = null
  }

  private val targetRun = TargetRun()

  private fun mapperFor(interpreter: NodeJsInterpreter, environment: com.intellij.platform.eel.EelDescriptor) =
    nodePathMapper(interpreter, environment, targetRun) { EelServerPathMapper(it, ::toEelPath) }

  fun testContainersNodeMapsFromTheEnvironmentWithoutAskingTheRun() {
    val mapper = mapperFor(EnvironmentInterpreter(), container)

    assertEquals("/workspaces/app/a.ts", mapper.toServerPath("$containerRoot/workspaces/app/a.ts"))
    assertEquals("$containerRoot/workspaces/app/a.ts", mapper.toLocalPath("/workspaces/app/a.ts"))
    assertEquals("The run was asked", emptyList<String>(), targetRun.asked)
  }

  /** The LSP client converts paths on the UI thread. */
  fun testContainersNodeConversionsDoNotBlock() {
    val mapper = mapperFor(EnvironmentInterpreter(), container)

    assertDoesNotBlock {
      mapper.toServerPath("$containerRoot/workspaces/app/a.ts")
      mapper.toLocalPath("/workspaces/app/a.ts")
    }
  }

  fun testPathOutsideTheContainerFallsBackToTheRun() {
    val local = Path.of("/Users/me/a.ts").toAbsolutePath().toString()

    assertEquals("target:$local", mapperFor(EnvironmentInterpreter(), container).toServerPath(local))
  }

  fun testLocalInterpreterStillMapsThroughTheRun() {
    val mapper = mapperFor(NodeJsLocalInterpreter("/usr/bin/node"), container)

    assertEquals("target:$containerRoot/workspaces/app/a.ts", mapper.toServerPath("$containerRoot/workspaces/app/a.ts"))
  }

  fun testLocalProjectStillMapsThroughTheRun() {
    val mapper = mapperFor(EnvironmentInterpreter(), LocalEelDescriptor)

    assertEquals("target:/project/a.ts", mapper.toServerPath("/project/a.ts"))
  }
}
