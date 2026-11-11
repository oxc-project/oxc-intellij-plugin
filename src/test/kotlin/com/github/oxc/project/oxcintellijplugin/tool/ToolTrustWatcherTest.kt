package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.untrustProject
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import java.nio.file.Path

/** The platform starts no server in an untrusted project, and doesn't look at open files again on trust. */
class ToolTrustWatcherTest : OxcFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
    myFixture.copyDirectoryToProject("no-config", "")
  }

  fun testTrustingTheProjectStartsServersForOpenFiles() {
    openFileWhileUntrusted()

    TrustedProjects.setProjectTrusted(project, true)

    waitUntil("a server runs for the open file") { runningServers().isNotEmpty() }
  }

  /** Settings | Trusted Locations trusts a folder and names no project. */
  fun testTrustedLocationsStartsServersForOpenFiles() {
    openFileWhileUntrusted()

    // The project counts as trusted again without an event, as after Trusted Locations stores the folder.
    System.clearProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY)
    assertTrue(TrustedProjects.isProjectTrusted(project))
    ApplicationManager.getApplication().messageBus.syncPublisher(TrustedProjectsListener.TOPIC)
      .onProjectTrusted(TrustedProjectsLocator.locateProject(Path.of(project.basePath!!), null))

    waitUntil("a server runs for the open file") { runningServers().isNotEmpty() }
  }

  private fun openFileWhileUntrusted() {
    untrustProject(project, testRootDisposable)
    myFixture.configureFromTempProjectFile("index.js")
    repeat(10) { Thread.sleep(100); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertEmpty("No server starts while the project is untrusted", allServers())
  }

  private fun allServers() =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)

  private fun runningServers() = allServers().filter { it.state == LspServerState.Running }
}
