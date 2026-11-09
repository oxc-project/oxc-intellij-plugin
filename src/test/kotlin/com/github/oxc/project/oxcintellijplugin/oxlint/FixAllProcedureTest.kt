package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.untrustProject
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Uses a server with scripted answers. Needs Node on PATH. */
class FixAllProcedureTest : OxcFixtureTestCase() {

  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    val settings = OxlintSettings.getInstance(project)
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = File("src/test/testData/oxlint/fix-all/fake-server.js").absolutePath
    file = myFixture.addFileToProject("index.js", "original\n").virtualFile
  }

  fun testCachedActionIsAppliedWithoutPullingDiagnostics() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", text())
    assertSize(1, fixAllRequests())
  }

  fun testEmptyCacheIsRefilledByPullingDiagnostics() {
    script("""{ "afterDiagnostic": [${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", text())
    assertSize(2, fixAllRequests())
  }

  fun testOnlyPreferredActionsAreApplied() {
    script("""{ "cached": [${action("not preferred\n", preferred = false)}, ${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", text())
  }

  fun testNothingIsAppliedWhenNoActionIsPreferred() {
    script("""{ "cached": [${action("not preferred\n", preferred = false)}] }""")

    assertEquals(OxcFixAllResult.NothingToApply, runFixAll())

    assertEquals("original\n", text())
  }

  fun testServerThatNeverAnswersTimesOut() {
    script("""{ "hang": true }""")
    val timeout = Registry.get("oxc.lint.fix.all.timeout.ms")
    timeout.setValue(500, testRootDisposable)

    assertEquals(OxcFixAllResult.TimedOut, runFixAll())

    assertEquals("original\n", text())
  }

  fun testFixKindNoneAsksTheServerForNothing() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    OxlintSettings.getInstance(project).state.fixKind = OxlintFixKind.NONE

    assertEquals(OxcFixAllResult.FixesTurnedOff, runFixAll())

    assertEquals("original\n", text())
    assertEmpty(requestedMethods())
    assertEmpty(allServers())
  }

  fun testSafeFixKindsAskForTheSafeAction() {
    for (kind in listOf(OxlintFixKind.SAFE_FIX, OxlintFixKind.SAFE_FIX_OR_SUGGESTION)) {
      assertEquals("source.fixAll.oxc", requestedKind(kind))
    }
  }

  fun testDangerousFixKindsAskForTheDangerousAction() {
    for (kind in listOf(OxlintFixKind.DANGEROUS_FIX, OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION, OxlintFixKind.ALL)) {
      assertEquals("source.fixAllDangerous.oxc", requestedKind(kind))
    }
  }

  fun testUnsupportedFileIsReported() {
    val text = myFixture.addFileToProject("notes.txt", "original\n").virtualFile

    assertEquals(OxcFixAllResult.UnsupportedFile, runFixAll(text))
  }

  fun testDisabledAsksTheServerForNothing() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.DISABLED

    assertEquals(OxcFixAllResult.NothingToApply, runFixAll())

    assertEmpty(requestedMethods())
  }

  fun testFixKindNoneDoesNotTouchARunningServer() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") { runningServers().size == 1 }
    OxlintSettings.getInstance(project).state.fixKind = OxlintFixKind.NONE

    assertEquals(OxcFixAllResult.FixesTurnedOff, runFixAll())

    assertEquals("original\n", text())
    assertEmpty(fixAllRequests())
  }

  fun testOpenFileWhoseServerIsStillStartingWaitsForIt() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)

    assertEquals(OxcFixAllResult.Applied, runFixAll())
  }

  fun testAutomaticModeWithNoPackageIsReported() {
    script("{}")
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.AUTOMATIC

    assertEquals(OxcFixAllResult.ToolNotFound, runFixAll())
    assertEmpty(allServers())
  }

  fun testAutomaticModeWithAPackageThatIsDeclaredButNotInstalledIsReported() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "oxlint": "1.0.0" } }""")
    script("{}")
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.AUTOMATIC

    assertEquals(OxcFixAllResult.ToolNotFound, runFixAll())
  }

  fun testManualModeWithNoPathAndNoPackageIsReported() {
    script("{}")
    OxlintSettings.getInstance(project).state.binaryPath = ""

    assertEquals(OxcFixAllResult.ToolNotFound, runFixAll())
  }

  fun testServerThatCannotStartFails() {
    script("{}")
    shortenTheServerConnectTimeout()
    OxlintSettings.getInstance(project).state.binaryPath = "${myFixture.tempDirPath}/missing"

    assertInstanceOf(runFixAll(), OxcFixAllResult.Failed::class.java)
    waitForThePlatformToFinishStopping()
  }

  // Windows can't run this script. There, testServerThatCannotStartFails covers this with a WSL project.
  fun testNativeServerThatExitsBeforeAnsweringFails() {
    if (SystemInfo.isWindows) return

    script("{}")
    shortenTheServerConnectTimeout()
    val server = Files.createTempFile(serverRoot(), "exiting-server", null)
    Files.writeString(server, "#!/bin/sh\nread line\nexit 1\n")
    server.toFile().setExecutable(true)
    OxlintSettings.getInstance(project).state.binaryPath = server.toString()

    assertInstanceOf(runFixAll(), OxcFixAllResult.Failed::class.java)
    waitForThePlatformToFinishStopping()
  }

  /** The platform starts no server here and says nothing, so Fix All used to wait for the whole timeout. */
  fun testUntrustedProjectRunsNothingAndSaysSo() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    untrustProject(project, testRootDisposable)

    assertEquals(OxcFixAllResult.Untrusted, runFixAll())

    assertEquals("original\n", text())
    assertEquals("original\n", String(file.contentsToByteArray()))
    assertEmpty(allServers())
    assertEmpty(requestedMethods())
  }

  fun testClosedFileIsSavedAndTheServerStopsAfterwards() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", String(file.contentsToByteArray()))
    waitUntil("the server has stopped") { runningServers().isEmpty() }
  }

  fun testClosedFileWhoseServerNeverAnswersStopsTheServerAfterTheTimeout() {
    script("""{ "hang": true }""")
    Registry.get("oxc.lint.fix.all.timeout.ms").setValue(2_000, testRootDisposable)

    assertEquals(OxcFixAllResult.TimedOut, runFixAll())

    waitUntil("the server has stopped") { liveServers().isEmpty() }
  }

  fun testCancelledFixAllForAClosedFileStopsTheServer() {
    script("""{ "hang": true }""")
    val job = CoroutineScope(Dispatchers.Default).launch { OxcFixAll.getInstance(project).fixFile(OxlintDefinition, file) }
    waitUntil("Fix All has asked the server") { fixAllRequests().isNotEmpty() }

    job.cancel()

    waitUntil("Fix All has finished") { job.isCompleted }
    waitUntil("the server has stopped") { liveServers().isEmpty() }
  }

  /** Before, Fix All only left its wait for a server that crashed, so this waited for the whole timeout. */
  fun testServerStoppedWhileStartingFailsWithoutWaitingForTheTimeout() {
    script("""{ "initializeDelayMs": 2000, "cached": [${action("fixed\n", preferred = true)}] }""")
    Registry.get("oxc.lint.fix.all.timeout.ms").setValue(120_000, testRootDisposable)
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxlintDefinition, file) }
    }
    waitUntil("the server is starting") { allServers().any { it.state == LspServerState.Initializing } }
    // Fix All looks every 50 ms, and a stopped client leaves the list at once, so let it see the client first.
    Thread.sleep(200)

    LspClientManager.getInstance(project).stopClients(OxlintLspIntegrationProvider::class.java)

    assertInstanceOf(PlatformTestUtil.waitForFuture(call, 30_000), OxcFixAllResult.Failed::class.java)
    assertEquals("original\n", String(file.contentsToByteArray()))
  }

  /** The action starts the server before its modal dialog, and the fix that follows stops it. */
  fun testStartForAClosedFileLeavesTheServerForTheFixWhichStopsIt() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")

    assertNull(startServerForFile())
    assertNotEmpty(runningServers())

    assertEquals(OxcFixAllResult.Applied, runFixAll())
    waitUntil("the server has stopped") { liveServers().isEmpty() }
  }

  /**
   * The stop mustn't wait for the server to initialize, which the platform's shutdown does off the EDT.
   * The delay is longer than the timeout, and short enough for that shutdown to end within the test.
   */
  fun testStartThatTimesOutForAClosedFileStopsTheServer() {
    script("""{ "initializeDelayMs": 3000 }""")
    Registry.get("oxc.lint.fix.all.timeout.ms").setValue(1_000, testRootDisposable)
    val start = System.nanoTime()

    assertEquals(OxcFixAllResult.TimedOut, startServerForFile())

    assertTrue("Fix All waited for the server to initialize", System.nanoTime() - start < 2_500_000_000)
    waitUntil("the server has stopped") { liveServers().isEmpty() }
    waitUntil("the server's process has exited") { fakeServerProcesses().isEmpty() }
  }

  /** The fix is positions in the text the server saw, so text typed while it worked must not be overwritten. */
  fun testDocumentChangedWhileTheServerWorksIsLeftAlone() {
    script("""{ "answerDelayMs": 2000, "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") { runningServers().size == 1 }
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxlintDefinition, file) }
    }
    waitUntil("Fix All has asked the server") { fixAllRequests().isNotEmpty() }

    val document = myFixture.editor.document
    WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "typed\n") }

    assertEquals(OxcFixAllResult.NothingToApply, PlatformTestUtil.waitForFuture(call, 60_000))
    assertEquals("original\ntyped\n", document.text)
  }

  fun testOpenFileIsLeftUnsavedAndTheServerKeepsRunning() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") { runningServers().size == 1 }

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", myFixture.editor.document.text)
    assertEquals("original\n", String(file.contentsToByteArray()))
    assertEquals(1, runningServers().size)
  }

  /** Only for servers that won't answer, because a real one can take longer than this to start. */
  private fun shortenTheServerConnectTimeout() {
    Registry.get("lsp.server.connect.timeout").setValue(1, testRootDisposable)
  }

  /**
   * The platform waits for the answer to `initialize` while holding the lock that stopping the server
   * needs, even once the server has exited. A thread still doing that fails the test's thread leak check.
   */
  private fun waitForThePlatformToFinishStopping() {
    waitUntil("the platform has finished stopping the server") {
      Thread.getAllStackTraces().values.none { stack ->
        stack.any { it.methodName == "initializeLsp4jServer" || it.methodName.startsWith("shutdownAndExit") }
      }
    }
  }

  private fun requestedKind(kind: OxlintFixKind): String {
    script("{}")
    Files.deleteIfExists(requestLog())
    OxlintSettings.getInstance(project).state.fixKind = kind
    runFixAll()
    val request = Files.readAllLines(requestLog()).first { "textDocument/codeAction" in it }
    return Regex(""""only":\["([^"]+)"]""").find(request)!!.groupValues[1]
  }

  private fun action(insert: String, preferred: Boolean): String =
    """{ "title": "fix", "isPreferred": $preferred, "insert": ${"\"" + insert.replace("\n", "\\n") + "\""} }"""

  private fun script(json: String) {
    Files.writeString(serverRoot().resolve("fake-server.json"), json)
  }

  private fun requestLog(): Path = serverRoot().resolve("requests.log")

  /**
   * What Fix All asks for. Fix All only pulls diagnostics between two of these, so their number shows if
   * it pulled. Counting the pulls wouldn't work, because the platform also pulls them on its own.
   */
  private fun fixAllRequests(): List<String> {
    if (!Files.exists(requestLog())) return emptyList()
    return Files.readAllLines(requestLog()).filter { "textDocument/codeAction" in it && "source.fixAll" in it }
  }

  private fun requestedMethods(): List<String> {
    if (!Files.exists(requestLog())) return emptyList()
    return Files.readAllLines(requestLog()).map { Regex(""""method":"([^"]+)"""").find(it)!!.groupValues[1] }
      .filterNot { it == "initialize" || it == "shutdown" }
  }

  private fun serverRoot(): Path = Path.of(file.parent.path)

  private fun text(): String =
    com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!.text

  private fun fakeServerProcesses(): List<ProcessHandle> =
    ProcessHandle.current().descendants().filter { "fake-server.js" in it.info().commandLine().orElse("") }.toList()

  private fun startServerForFile(): OxcFixAllResult? {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult?> {
      runBlocking { OxcFixAll.getInstance(project).startServerForFile(OxlintDefinition, file) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }

  private fun runFixAll(target: VirtualFile = file): OxcFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxlintDefinition, target) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }

  private fun allServers() =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)

  private fun liveServers() =
    allServers().filter { it.state == LspServerState.Running || it.state == LspServerState.Initializing }

  private fun runningServers() =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)
      .filter { it.state == LspServerState.Running }
}
