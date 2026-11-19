package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.runFixAllAction
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.notification.Notification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import kotlinx.coroutines.runBlocking
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Starts a real Oxlint server. Needs Node on PATH and `npm ci` run in the fixture folder. `no-var` has a
 * safe fix. The server's Fix All leaves `no-debugger` alone at every fix kind, as it only has a suggestion.
 */
@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class FixAllRealServerTest : OxcFixtureTestCase() {

  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
    myFixture.copyDirectoryToProject("no-config", "")
    myFixture.addFileToProject(".oxlintrc.json", """{ "rules": { "no-var": "error", "no-debugger": "error" } }""")
    file = myFixture.addFileToProject("fix.js", "var fixed = 1;\nconsole.log(fixed);\ndebugger;\n").virtualFile
  }

  fun testOpenFileIsFixed() {
    openAndWaitForDiagnostics()

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("const fixed = 1;\nconsole.log(fixed);\ndebugger;\n", myFixture.editor.document.text)
  }

  fun testClosedFileIsFixedAndSaved() {
    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("const fixed = 1;\nconsole.log(fixed);\ndebugger;\n", String(file.contentsToByteArray()))
  }

  /** No server runs for a closed file, and the platform can't start one behind the action's modal dialog. */
  fun testClosedFileIsFixedThroughTheAction() {
    assertEquals("Fixes applied successfully", runAction().title)

    assertEquals("const fixed = 1;\nconsole.log(fixed);\ndebugger;\n", String(file.contentsToByteArray()))
  }

  fun testOpenFileIsFixedThroughTheAction() {
    openAndWaitForDiagnostics()

    assertEquals("Fixes applied successfully", runAction().title)

    assertEquals("const fixed = 1;\nconsole.log(fixed);\ndebugger;\n", myFixture.editor.document.text)
  }

  /**
   * A change made outside the IDE that the file watcher hasn't reported yet, such as a checkout just
   * before the action. The document already holds the old text.
   */
  fun testClosedFileChangedOnDiskIsFixedFromTheDisk() {
    val staleDocument = FileDocumentManager.getInstance().getDocument(file)
    file.toNioPath().writeText("var changed = 2;\nconsole.log(changed);\n")

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertEquals("const changed = 2;\nconsole.log(changed);\n", file.toNioPath().readText())
    assertEquals("const changed = 2;\nconsole.log(changed);\n", staleDocument?.text)
  }

  fun testDangerousFixKindAppliesFixes() {
    OxlintSettings.getInstance(project).state.fixKind = OxlintFixKind.ALL
    openAndWaitForDiagnostics()

    assertEquals(OxcFixAllResult.Applied, runFixAll())

    assertTrue(myFixture.editor.document.text, "const fixed" in myFixture.editor.document.text)
  }

  fun testSavingRunsTheFixesWhenEnabled() {
    OxlintSettings.getInstance(project).state.fixAllOnSave = true
    openAndWaitForDiagnostics()
    typeSomething()

    FileDocumentManager.getInstance().saveAllDocuments()

    waitUntil("the fixes have been saved") { "const fixed" in String(file.contentsToByteArray()) }
  }

  fun testSavingLeavesTheFileAloneWhenDisabled() {
    openAndWaitForDiagnostics()
    typeSomething()
    val typed = myFixture.editor.document.text

    FileDocumentManager.getInstance().saveAllDocuments()
    repeat(20) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      Thread.sleep(100)
    }

    // Exactly what was typed, so a save that wrote nothing fails too.
    assertEquals("// edited\nvar fixed = 1;\nconsole.log(fixed);\ndebugger;\n", typed)
    assertFalse("The document should have been saved", FileDocumentManager.getInstance().isFileModified(file))
    assertEquals(typed, String(file.contentsToByteArray()))
  }

  private fun typeSomething() {
    val document = myFixture.editor.document
    WriteAction.runAndWait<Exception> {
      CommandProcessor.getInstance().runUndoTransparentAction { document.insertString(0, "// edited\n") }
    }
  }

  private fun openAndWaitForDiagnostics() {
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server has reported diagnostics") {
      myFixture.doHighlighting().any { "no-var" in it.description.orEmpty() || "`var`" in it.description.orEmpty() }
    }
  }

  /** The tool's timeout is shorter than the wait for the notification, so a timeout fails the test. */
  private fun runAction(): Notification {
    Registry.get("oxc.lint.fix.all.timeout.ms").setValue(10_000, testRootDisposable)
    return runFixAllAction(project, testRootDisposable, "OxlintApplyFixAllAction", file)
  }

  private fun runFixAll(): OxcFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxlintDefinition, file) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }
}
