package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.fix.OxfmtFixAllOnSaveAction
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.frontend.tool.FixOnSaveFailureNotifier
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** Uses a server with scripted answers. Needs Node on PATH. */
class OxfmtFormatProcedureTest : OxcFixtureTestCase() {

  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.DISABLED
    val settings = OxfmtSettings.getInstance(project)
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = File("src/test/testData/oxlint/fix-all/fake-server.js").absolutePath
    file = myFixture.addFileToProject("index.js", "one\ntwo\nthree\n").virtualFile
  }

  fun testSeveralEditsInOrderFromTheStartOfTheFile() {
    script("""{ "formatting": [${edit(0, 0, 0, 3, "first line")}, ${edit(2, 0, 2, 5, "3")}] }""")

    assertEquals(OxcFixAllResult.Applied, runFormat())

    assertEquals("first line\ntwo\n3\n", text())
  }

  fun testSeveralEditsInOrderFromTheEndOfTheFile() {
    script("""{ "formatting": [${edit(2, 0, 2, 5, "3")}, ${edit(0, 0, 0, 3, "first line")}] }""")

    assertEquals(OxcFixAllResult.Applied, runFormat())

    assertEquals("first line\ntwo\n3\n", text())
  }

  fun testEditThatAddsLinesDoesNotMoveALaterEdit() {
    script("""{ "formatting": [${edit(0, 0, 0, 3, "a\\nb\\nc")}, ${edit(1, 0, 1, 3, "2")}] }""")

    assertEquals(OxcFixAllResult.Applied, runFormat())

    assertEquals("a\nb\nc\n2\nthree\n", text())
  }

  fun testWindowsLineEndingsAreNormalized() {
    script("""{ "formatting": [${edit(0, 0, 0, 3, "a\\r\\nb")}] }""")

    assertEquals(OxcFixAllResult.Applied, runFormat())

    assertEquals("a\nb\ntwo\nthree\n", text())
  }

  fun testEditThatOnlyDiffersInLineEndingsIsNothingToApply() {
    script("""{ "formatting": [${edit(0, 0, 1, 3, "one\\r\\ntwo")}] }""")

    assertEquals(OxcFixAllResult.NothingToApply, runFormat())
  }

  fun testNoEditsIsNothingToApply() {
    script("""{ "formatting": [] }""")

    assertEquals(OxcFixAllResult.NothingToApply, runFormat())

    assertEquals("one\ntwo\nthree\n", text())
  }

  fun testFormattingOptionsFollowTheCodeStyle() {
    script("""{ "formatting": [] }""")

    runFormat()

    val request = Files.readAllLines(requestLog()).first { "textDocument/formatting" in it }
    assertTrue(request, """"insertSpaces":true""" in request)
  }

  fun testServerThatNeverAnswersTimesOut() {
    script("""{ "hang": true }""")
    Registry.get("oxc.fmt.fix.all.timeout.ms").setValue(500, testRootDisposable)

    assertEquals(OxcFixAllResult.TimedOut, runFormat())
  }

  /** The save goes on without the formatting, so the user has to be told, as v0.0.45 did. */
  fun testFixOnSaveThatTimesOutTellsTheUser() {
    val notifications = collectNotifications()
    script("""{ "hang": true }""")
    Registry.get("oxc.fmt.fix.all.timeout.ms").setValue(500, testRootDisposable)
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") {
      LspClientManager.getInstance(project).getClients(OxfmtLspIntegrationProvider::class.java).any { it.state == LspServerState.Running }
    }

    runFixOnSave()

    waitUntil("the failure is shown") { notifications.isNotEmpty() }
    val notification = notifications.single()
    assertEquals(NotificationType.ERROR, notification.type)
    assertTrue(notification.content, "index.js" in notification.content)
    assertEquals("one\ntwo\nthree\n", text())
  }

  fun testFixOnSaveThatFormatsStaysQuiet() {
    val notifications = collectNotifications()
    script("""{ "formatting": [${edit(0, 0, 0, 3, "first line")}] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server is running") {
      LspClientManager.getInstance(project).getClients(OxfmtLspIntegrationProvider::class.java).any { it.state == LspServerState.Running }
    }

    runFixOnSave()
    repeat(10) { Thread.sleep(100); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    assertEquals("first line\ntwo\nthree\n", text())
    assertEmpty(notifications)
  }

  fun testUnsupportedFile() {
    val other = myFixture.addFileToProject("notes.txt", "").virtualFile

    assertEquals(OxcFixAllResult.UnsupportedFile, runFormat(other))
  }

  /** The edits are positions in the text the server saw, so text typed while it worked must not be overwritten. */
  fun testDocumentChangedWhileTheServerWorksIsLeftAlone() {
    script("""{ "answerDelayMs": 2000, "formatting": [${edit(0, 0, 0, 3, "first line")}] }""")
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, file) }
    }
    waitUntil("the server has been asked") {
      Files.exists(requestLog()) && Files.readAllLines(requestLog()).any { "textDocument/formatting" in it }
    }

    val document = FileDocumentManager.getInstance().getDocument(file)!!
    WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, "typed\n") }

    assertEquals(OxcFixAllResult.NothingToApply, PlatformTestUtil.waitForFuture(call, 60_000))
    assertEquals("one\ntwo\nthree\ntyped\n", text())
  }

  fun testDisabled() {
    OxfmtSettings.getInstance(project).state.configurationMode = ConfigurationMode.DISABLED

    assertEquals(OxcFixAllResult.NothingToApply, runFormat())
  }

  private fun edit(startLine: Int, startCharacter: Int, endLine: Int, endCharacter: Int, newText: String): String =
    """{ "start": [$startLine, $startCharacter], "end": [$endLine, $endCharacter], "newText": "$newText" }"""

  private fun script(json: String) {
    Files.writeString(serverRoot().resolve("fake-server.json"), json)
  }

  private fun requestLog(): Path = serverRoot().resolve("requests.log")

  private fun serverRoot(): Path = Path.of(file.parent.path)

  private fun text(): String = FileDocumentManager.getInstance().getDocument(file)!!.text

  /** The frontend shows these, once its notifier listens, which a test project's startup may not have reached yet. */
  private fun collectNotifications(): List<Notification> {
    val notifications = CopyOnWriteArrayList<Notification>()
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == "Oxc") notifications += notification
      }
    })
    project.service<FixOnSaveFailureNotifier>()
    return notifications
  }

  private fun runFixOnSave() {
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    val call = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking { OxfmtFixAllOnSaveAction().updateDocument(project, document) }
    }
    PlatformTestUtil.waitForFuture(call, 60_000)
  }

  private fun runFormat(target: VirtualFile = file): OxcFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, target) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }
}
