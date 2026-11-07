package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix.OxlintFixAll
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
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

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", text())
    assertFalse("textDocument/diagnostic" in requestedMethods())
  }

  fun testEmptyCacheIsRefilledByPullingDiagnostics() {
    script("""{ "afterDiagnostic": [${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", text())
    assertEquals(
      listOf("textDocument/codeAction", "textDocument/diagnostic", "textDocument/codeAction"),
      requestedMethods().filter { it == "textDocument/codeAction" || it == "textDocument/diagnostic" },
    )
  }

  fun testOnlyPreferredActionsAreApplied() {
    script("""{ "cached": [${action("not preferred\n", preferred = false)}, ${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", text())
  }

  fun testNothingIsAppliedWhenNoActionIsPreferred() {
    script("""{ "cached": [${action("not preferred\n", preferred = false)}] }""")

    assertEquals(OxlintFixAllResult.NothingToApply, runFixAll())

    assertEquals("original\n", text())
  }

  fun testServerThatNeverAnswersTimesOut() {
    script("""{ "hang": true }""")
    val timeout = Registry.get("oxc.lint.fix.all.timeout.ms")
    timeout.setValue(500, testRootDisposable)

    assertEquals(OxlintFixAllResult.TimedOut, runFixAll())

    assertEquals("original\n", text())
  }

  fun testFixKindNoneAsksTheServerForNothing() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    OxlintSettings.getInstance(project).state.fixKind = OxlintFixKind.NONE

    assertEquals(OxlintFixAllResult.FixesTurnedOff, runFixAll())

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

    assertEquals(OxlintFixAllResult.UnsupportedFile, runFixAll(text))
  }

  fun testDisabledAsksTheServerForNothing() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.DISABLED

    assertEquals(OxlintFixAllResult.NothingToApply, runFixAll())

    assertEmpty(requestedMethods())
  }

  fun testFixKindNoneDoesNotTouchARunningServer() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") { runningServers().size == 1 }
    val requestsBefore = requestedMethods().filter { it == "textDocument/codeAction" || it == "textDocument/diagnostic" }.size
    OxlintSettings.getInstance(project).state.fixKind = OxlintFixKind.NONE

    assertEquals(OxlintFixAllResult.FixesTurnedOff, runFixAll())

    assertEquals("original\n", text())
    val requestsAfter = requestedMethods().filter { it == "textDocument/codeAction" || it == "textDocument/diagnostic" }.size
    assertEquals(requestsBefore, requestsAfter)
  }

  fun testOpenFileWhoseServerIsStillStartingWaitsForIt() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)

    assertEquals(OxlintFixAllResult.Applied, runFixAll())
  }

  fun testAutomaticModeWithNoPackageIsReported() {
    script("{}")
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.AUTOMATIC

    assertEquals(OxlintFixAllResult.OxlintNotFound, runFixAll())
    assertEmpty(allServers())
  }

  fun testAutomaticModeWithAPackageThatIsDeclaredButNotInstalledIsReported() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "oxlint": "1.0.0" } }""")
    script("{}")
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.AUTOMATIC

    assertEquals(OxlintFixAllResult.OxlintNotFound, runFixAll())
  }

  fun testManualModeWithNoPathAndNoPackageIsReported() {
    script("{}")
    OxlintSettings.getInstance(project).state.binaryPath = ""

    assertEquals(OxlintFixAllResult.OxlintNotFound, runFixAll())
  }

  fun testServerThatCannotStartFails() {
    script("{}")
    shortenTheServerConnectTimeout()
    OxlintSettings.getInstance(project).state.binaryPath = "${myFixture.tempDirPath}/missing"

    assertInstanceOf(runFixAll(), OxlintFixAllResult.Failed::class.java)
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

    assertInstanceOf(runFixAll(), OxlintFixAllResult.Failed::class.java)
    waitForThePlatformToFinishStopping()
  }

  fun testClosedFileIsSavedAndTheServerStopsAfterwards() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertEquals("fixed\noriginal\n", String(file.contentsToByteArray()))
    waitUntil("the server has stopped") { runningServers().isEmpty() }
  }

  fun testOpenFileIsLeftUnsavedAndTheServerKeepsRunning() {
    script("""{ "cached": [${action("fixed\n", preferred = true)}] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") { runningServers().size == 1 }

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

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

  private fun requestedMethods(): List<String> {
    if (!Files.exists(requestLog())) return emptyList()
    return Files.readAllLines(requestLog()).map { Regex(""""method":"([^"]+)"""").find(it)!!.groupValues[1] }
      .filterNot { it == "initialize" || it == "shutdown" }
  }

  private fun serverRoot(): Path = Path.of(file.parent.path)

  private fun text(): String =
    com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!.text

  private fun runFixAll(target: VirtualFile = file): OxlintFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxlintFixAllResult> {
      runBlocking { OxlintFixAll.getInstance(project).fixFile(target) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }

  private fun allServers() =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)

  private fun runningServers() =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)
      .filter { it.state == LspServerState.Running }
}
