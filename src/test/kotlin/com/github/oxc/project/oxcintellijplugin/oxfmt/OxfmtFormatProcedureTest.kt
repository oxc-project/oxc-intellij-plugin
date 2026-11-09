package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

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

  private fun runFormat(target: VirtualFile = file): OxcFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, target) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }
}
