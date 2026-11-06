package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.runBlocking

/** Starts a real Oxlint server. Needs Node on PATH and `npm ci` run in the fixture folder. */
@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class ServerLifecycleTest : OxcFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
    myFixture.copyDirectoryToProject("no-config", "")
  }

  fun testClosingTheLastSupportedFileStopsTheServerAndOpeningOneStartsIt() {
    val file = myFixture.configureFromTempProjectFile("index.js").virtualFile
    val firstServer = runningServer()

    FileEditorManager.getInstance(project).closeFile(file)
    waitUntil("the server has stopped") { runningServers().isEmpty() }

    myFixture.configureFromTempProjectFile("index.js")
    assertNotSame(firstServer, runningServer())
  }

  fun testClosingAnUnsupportedFileKeepsTheServerWhileASupportedFileIsOpen() {
    val unsupportedFile = myFixture.configureFromTempProjectFile("package.json").virtualFile
    myFixture.configureFromTempProjectFile("index.js")
    val server = runningServer()

    FileEditorManager.getInstance(project).closeFile(unsupportedFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertSame(server, runningServer())
  }

  fun testClosingOneOfTwoSupportedFilesKeepsTheServer() {
    val closedFile = myFixture.configureFromTempProjectFile("index.js").virtualFile
    myFixture.configureFromTempProjectFile("index.expected.js")
    val server = runningServer()

    FileEditorManager.getInstance(project).closeFile(closedFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertSame(server, runningServer())
  }

  fun testRestartOverRpcReplacesTheServer() {
    myFixture.configureFromTempProjectFile("index.js")
    val firstServer = runningServer()

    restartOverRpc()

    waitUntil("a new server is running") { runningServers().singleOrNull().let { it != null && it !== firstServer } }
  }

  fun testRestartOverRpcDoesNotStartAServerThatNoOpenFileNeeds() {
    restartOverRpc()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEmpty(runningServers())
  }

  /** The backend restarts on the UI thread, so this has to wait without blocking it. */
  private fun restartOverRpc() {
    val projectId = project.projectId()
    val restart = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking { OxlintServerApi.getInstance().restart(projectId) }
    }
    PlatformTestUtil.waitForFuture(restart, 30_000)
  }

  private fun runningServer(): LspClient {
    waitUntil("a server is running") { runningServers().size == 1 }
    return runningServers().single()
  }

  private fun runningServers(): List<LspClient> =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)
      .filter { it.state == LspServerState.Running }
}
