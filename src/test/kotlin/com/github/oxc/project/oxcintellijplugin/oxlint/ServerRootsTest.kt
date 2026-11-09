package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import java.io.File

/**
 * A monorepo with two packages, each with its own `oxlint`, so each runs its own server. Uses the scripted
 * server as the manual binary, which runs from the root of the package it finds. Needs Node on PATH.
 */
class ServerRootsTest : OxcFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    val settings = OxlintSettings.getInstance(project)
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = File("src/test/testData/oxlint/fix-all/fake-server.js").absolutePath
    for (root in listOf("a", "b")) {
      myFixture.addFileToProject("$root/package.json", """{ "name": "$root" }""")
      myFixture.addFileToProject("$root/node_modules/oxlint/package.json", """{ "name": "oxlint", "version": "1.0.0" }""")
      myFixture.addFileToProject("$root/index.js", "")
      myFixture.addFileToProject("$root/other.js", "")
    }
  }

  fun testClosingARootsLastFileStopsOnlyThatRootsServer() {
    val closed = open("a/index.js")
    open("b/index.js")
    waitUntil("both servers are running") { runningRoots() == setOf("a", "b") }

    FileEditorManager.getInstance(project).closeFile(closed)

    waitUntil("only b's server is running") { runningRoots() == setOf("b") && liveServers().size == 1 }
  }

  fun testClosingAFileWhoseRootStillHasOneOpenKeepsEveryServer() {
    val closed = open("a/index.js")
    open("a/other.js")
    open("b/index.js")
    waitUntil("both servers are running") { runningRoots() == setOf("a", "b") }
    val servers = runningServers().toSet()

    FileEditorManager.getInstance(project).closeFile(closed)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals(servers, runningServers().toSet())
  }

  private fun open(path: String): VirtualFile = myFixture.configureFromTempProjectFile(path).virtualFile

  private fun allServers(): Collection<LspClient> =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)

  private fun liveServers(): List<LspClient> =
    allServers().filter { it.state == LspServerState.Initializing || it.state == LspServerState.Running }

  private fun runningServers(): List<LspClient> = allServers().filter { it.state == LspServerState.Running }

  private fun runningRoots(): Set<String> = runningServers().flatMap { client -> client.descriptor.roots.map { it.name } }.toSet()
}
