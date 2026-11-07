package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix.OxlintFixAll
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import kotlinx.coroutines.runBlocking

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

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertEquals("const fixed = 1;\nconsole.log(fixed);\ndebugger;\n", myFixture.editor.document.text)
  }

  fun testClosedFileIsFixedAndSaved() {
    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertEquals("const fixed = 1;\nconsole.log(fixed);\ndebugger;\n", String(file.contentsToByteArray()))
  }

  fun testDangerousFixKindAppliesFixes() {
    OxlintSettings.getInstance(project).state.fixKind = OxlintFixKind.ALL
    openAndWaitForDiagnostics()

    assertEquals(OxlintFixAllResult.Applied, runFixAll())

    assertTrue(myFixture.editor.document.text, "const fixed" in myFixture.editor.document.text)
  }

  private fun openAndWaitForDiagnostics() {
    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("the server has reported diagnostics") {
      myFixture.doHighlighting().any { "no-var" in it.description.orEmpty() || "`var`" in it.description.orEmpty() }
    }
  }

  private fun runFixAll(): OxlintFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxlintFixAllResult> {
      runBlocking { OxlintFixAll.getInstance(project).fixFile(file) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }
}
