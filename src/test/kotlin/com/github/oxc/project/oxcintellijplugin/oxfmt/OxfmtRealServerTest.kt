package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.runFixAllAction
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.ConfigurationItem

private const val ROOT_CONFIG_RESULT = "const greeting = 'Happy developing ✨';\n"
private const val NESTED_CONFIG_RESULT = "const greeting = \"Happy developing ✨\"\n"

/**
 * Starts a real Oxfmt server. Needs Node on PATH and `npm ci` run in the fixture folder. The root config
 * asks for single quotes, and the nested one for no semicolons.
 */
@TestDataPath("\$CONTENT_ROOT/testData/oxfmt/formatting")
class OxfmtRealServerTest : OxcFixtureTestCase() {

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  override fun setUp() {
    super.setUp()
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.DISABLED
    myFixture.testDataPath = "src/test/testData/oxfmt/formatting"
    myFixture.copyDirectoryToProject("nested-config", "")
  }

  fun testRootFileUsesRootConfig() {
    val file = open("index.js")

    assertEquals(OxcFixAllResult.Applied, runFormat(file))

    assertEquals(ROOT_CONFIG_RESULT, myFixture.editor.document.text)
  }

  fun testSubdirectoryFileUsesNestedConfig() {
    val file = open("subdirectory/index.js")

    // The file already matches the nested config.
    assertEquals(OxcFixAllResult.NothingToApply, runFormat(file))

    assertEquals(NESTED_CONFIG_RESULT.trimEnd(), myFixture.editor.document.text.trimEnd())
  }

  fun testSubdirectoryFileUsesRootConfigWhenNestedConfigDisabled() {
    settings.state.disableNestedConfig = true
    val file = open("subdirectory/index.js")

    assertEquals(OxcFixAllResult.Applied, runFormat(file))

    assertEquals(ROOT_CONFIG_RESULT, myFixture.editor.document.text)
  }

  fun testFormattedFileHasNothingToApply() {
    val file = myFixture.addFileToProject("formatted.js", ROOT_CONFIG_RESULT).virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    assertEquals(OxcFixAllResult.NothingToApply, runFormat(file))
  }

  fun testFileThatIsNotJavaScriptIsFormatted() {
    val file = myFixture.addFileToProject("style.css", "a{color:red}\n").virtualFile
    myFixture.configureFromExistingVirtualFile(file)

    assertEquals(OxcFixAllResult.Applied, runFormat(file))

    assertEquals("a {\n  color: red;\n}\n", myFixture.editor.document.text)
  }

  fun testClosedFileIsFormattedAndSavedAndTheServerStops() {
    val file = myFixture.findFileInTempDir("index.js")
    assertFalse(file in FileEditorManager.getInstance(project).openFiles)

    assertEquals(OxcFixAllResult.Applied, runFormat(file))

    assertEquals(ROOT_CONFIG_RESULT, String(file.contentsToByteArray()))
    waitUntil("the server has stopped") { runningServers().isEmpty() }
  }

  /** No server runs for a closed file, and the platform can't start one behind the action's modal dialog. */
  fun testClosedFileIsFormattedThroughTheAction() {
    val file = myFixture.findFileInTempDir("index.js")
    Registry.get("oxc.fmt.fix.all.timeout.ms").setValue(10_000, testRootDisposable)

    val notification = runFixAllAction(project, testRootDisposable, "OxfmtApplyFixAllAction", file)

    assertEquals("Fixes applied successfully", notification.title)
    assertEquals(ROOT_CONFIG_RESULT, String(file.contentsToByteArray()))
    waitUntil("the server has stopped") { runningServers().isEmpty() }
  }

  fun testReformatCodeUsesOxfmt() {
    open("index.js")
    waitUntil("the server is running") { runningServers().isNotEmpty() }

    waitUntil("the file has been reformatted", ) {
      ReformatCodeProcessor(project, myFixture.file, null, false).run()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      myFixture.editor.document.text == ROOT_CONFIG_RESULT
    }
  }

  fun testSavingFormatsWhenEnabled() {
    settings.state.fixAllOnSave = true
    val file = open("index.js")
    waitUntil("the server is running") { runningServers().isNotEmpty() }
    typeSomething()

    FileDocumentManager.getInstance().saveAllDocuments()

    waitUntil("the formatted text has been saved") {
      String(file.contentsToByteArray()) == "// edited\n$ROOT_CONFIG_RESULT"
    }
  }

  fun testSavingLeavesTheFileAloneWhenDisabled() {
    val file = open("index.js")
    waitUntil("the server is running") { runningServers().isNotEmpty() }
    typeSomething()
    val typed = myFixture.editor.document.text

    FileDocumentManager.getInstance().saveAllDocuments()
    repeat(20) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      Thread.sleep(100)
    }

    // Exactly what was typed, unformatted, so a save that wrote nothing fails too.
    assertEquals("// edited\nconst greeting = \"Happy developing ✨\"\n", typed)
    assertFalse("The document should have been saved", FileDocumentManager.getInstance().isFileModified(file))
    assertEquals(typed, String(file.contentsToByteArray()))
  }

  /** Stands in for checking the server's code: everything it reads is under these two keys. */
  fun testOnlySendsTheKeysThatTheServerReads() {
    settings.state.disableNestedConfig = true
    open("index.js")
    waitUntil("the server is running") { runningServers().isNotEmpty() }
    val client = runningServers().single()
    val root = client.descriptor.roots.single()
    val item = ConfigurationItem().apply { scopeUri = client.descriptor.getFileUri(root) }

    assertEquals(
      mapOf("fmt.configPath" to null, "fmt.disableNestedConfig" to true),
      client.descriptor.getWorkspaceConfiguration(item),
    )
  }

  fun testClosingTheLastFileStopsTheServer() {
    val file = open("index.js")
    waitUntil("the server is running") { runningServers().isNotEmpty() }

    FileEditorManager.getInstance(project).closeFile(file)

    waitUntil("the server has stopped") { runningServers().isEmpty() }
  }

  /** Oxlint doesn't handle CSS, so only Oxlint's server has no file left to serve. */
  fun testEachToolStopsWhenItsOwnFilesClose() {
    val oxlint = OxlintSettings.getInstance(project)
    oxlint.state.configurationMode = ConfigurationMode.MANUAL
    oxlint.state.binaryPath = java.io.File("src/test/testData/oxlint/fix-all/fake-server.js").absolutePath
    val script = open("index.js")
    val style = myFixture.addFileToProject("style.css", "a{color:red}\n").virtualFile
    myFixture.configureFromExistingVirtualFile(style)
    waitUntil("both servers are running") { runningServers().isNotEmpty() && runningOxlintServers().isNotEmpty() }

    FileEditorManager.getInstance(project).closeFile(script)

    waitUntil("Oxlint's server has stopped") { runningOxlintServers().isEmpty() }
    assertFalse("Oxfmt still has a file open", runningServers().isEmpty())
  }

  private fun runningOxlintServers() =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)
      .filter { it.state == LspServerState.Running }

  fun testServersCanBeToldApartInTheLog() {
    open("index.js")
    waitUntil("the server is running") { runningServers().isNotEmpty() }

    assertEquals("Oxfmt@${project.name}", runningServers().single().descriptor.toString())
  }

  private fun open(path: String): VirtualFile = myFixture.configureFromTempProjectFile(path).virtualFile

  private fun typeSomething() {
    val document = myFixture.editor.document
    WriteAction.runAndWait<Exception> {
      CommandProcessor.getInstance().runUndoTransparentAction { document.insertString(0, "// edited\n") }
    }
  }

  private fun runningServers() =
    LspClientManager.getInstance(project).getClients(OxfmtLspIntegrationProvider::class.java)
      .filter { it.state == LspServerState.Running }

  private fun runFormat(file: VirtualFile): OxcFixAllResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxcFixAllResult> {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, file) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }
}
