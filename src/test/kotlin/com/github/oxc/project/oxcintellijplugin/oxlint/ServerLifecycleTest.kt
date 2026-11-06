package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintEditorWatcher
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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

  fun testClosingAClientsLastFileKeepsTheServerWhileAnotherClientHasOneOpen() {
    val file = myFixture.configureFromTempProjectFile("index.js").virtualFile
    val server = runningServer()

    val scope = CoroutineScope(Job())
    try {
      OxlintEditorWatcher().fileClosed(OtherClientHasFilesOpen(project, scope, listOf(file)), file)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
    finally {
      scope.cancel()
    }

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

  fun testApplyingSettingsRestartsTheServer() {
    myFixture.configureFromTempProjectFile("index.js")
    val firstServer = runningServer()

    val projectId = project.projectId()
    val apply = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking {
        val api = OxlintSettingsApi.getInstance()
        api.apply(projectId, api.load(projectId).copy(typeAware = true))
      }
    }
    PlatformTestUtil.waitForFuture(apply, 30_000)

    assertTrue(settings.state.typeAware)
    waitUntil("a new server is running") { runningServers().singleOrNull().let { it != null && it !== firstServer } }
  }

  fun testDisabledModeStartsNoServer() {
    settings.state.configurationMode = ConfigurationMode.DISABLED

    myFixture.configureFromTempProjectFile("index.js")
    waitAWhile()

    assertEmpty(allServers())
  }

  fun testDisablingStopsRunningServersAndEnablingStartsThem() {
    myFixture.configureFromTempProjectFile("index.js")
    runningServer()

    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.DISABLED))
    waitUntil("the server has stopped") { allServers().isEmpty() }

    settings.update(settings.toData().copy(configurationMode = ConfigurationMode.AUTOMATIC))
    runningServer()
  }

  fun testCustomExtensionsDecideWhichFilesStartAServer() {
    settings.state.supportedExtensions = mutableListOf(".json")

    myFixture.configureFromTempProjectFile("index.js")
    waitAWhile()
    assertEmpty(allServers())

    myFixture.configureFromTempProjectFile("package.json")
    runningServer()
  }

  fun testCustomExtensionsDecideWhichFilesKeepTheServer() {
    settings.state.supportedExtensions = mutableListOf(".json")
    val openFile = myFixture.configureFromTempProjectFile("package.json").virtualFile
    val unsupportedFile = myFixture.configureFromTempProjectFile("index.js").virtualFile
    val server = runningServer()

    FileEditorManager.getInstance(project).closeFile(unsupportedFile)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertSame(server, runningServer())

    FileEditorManager.getInstance(project).closeFile(openFile)
    waitUntil("the server has stopped") { runningServers().isEmpty() }
  }

  fun testManualModeRunsTheConfiguredScript() {
    val packageFolder = myFixture.findFileInTempDir("node_modules/oxlint/bin/oxlint")
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = packageFolder.path

    myFixture.configureFromTempProjectFile("index.js")

    runningServer()
  }

  private val settings: OxlintSettings
    get() = OxlintSettings.getInstance(project)

  /** Gives a server that shouldn't start the time to appear, so that its absence means something. */
  private fun waitAWhile() {
    repeat(10) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      Thread.sleep(100)
    }
  }

  private fun allServers(): Collection<LspClient> =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)

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

/**
 * What the manager reports while a client closes its last file and another client still has files
 * open. A light test project has only one client, so this can't come from a real second client.
 */
private class OtherClientHasFilesOpen(
  project: Project,
  scope: CoroutineScope,
  private val otherClientFiles: List<VirtualFile>,
) : FileEditorManagerImpl(project, scope) {

  override val openFiles: Array<VirtualFile>
    get() = VirtualFile.EMPTY_ARRAY

  override val openFilesWithRemotes: List<VirtualFile>
    get() = otherClientFiles
}
