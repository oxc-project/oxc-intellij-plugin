package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

/**
 * The server asks the client to watch its config files, and reloads them itself when told that one
 * changed, so the plugin doesn't have to restart it. These check that this stays true, using a real
 * server, because the plugin would otherwise keep showing results from the old config.
 */
@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class ConfigFileChangeTest : OxcFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
  }

  fun testCreatingChangingAndDeletingARootConfigAppliesWithoutARestart() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.configureFromTempProjectFile("index.js").virtualFile
    waitForDebuggerSeverity(HighlightSeverity.WARNING)
    val client = client()

    val config = createConfig(file.parent, """{ "rules": { "no-debugger": "off" } }""")
    waitForDebuggerSeverity(null)

    writeConfig(config, """{ "rules": { "no-debugger": "error" } }""")
    waitForDebuggerSeverity(HighlightSeverity.ERROR)

    WriteAction.runAndWait<Exception> { config.delete(this) }
    waitForDebuggerSeverity(HighlightSeverity.WARNING)

    assertSame("The server should not have been restarted", client, client())
  }

  fun testCreatingANestedConfigAppliesWithoutARestart() {
    myFixture.copyDirectoryToProject("no-config", "")
    myFixture.copyFileToProject("no-config/index.js", "nested/index.js")
    val file = myFixture.configureFromTempProjectFile("nested/index.js").virtualFile
    waitForDebuggerSeverity(HighlightSeverity.WARNING)
    val client = client()

    createConfig(file.parent, """{ "rules": { "no-debugger": "off" } }""")
    waitForDebuggerSeverity(null)

    assertSame("The server should not have been restarted", client, client())
  }

  private fun createConfig(folder: VirtualFile, content: String): VirtualFile =
    WriteAction.computeAndWait<VirtualFile, Exception> {
      folder.createChildData(this, ".oxlintrc.json").also { it.setBinaryContent(content.toByteArray()) }
    }

  private fun writeConfig(config: VirtualFile, content: String) {
    WriteAction.runAndWait<Exception> { config.setBinaryContent(content.toByteArray()) }
  }

  private fun client(): LspClient =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java).single()

  private fun waitForDebuggerSeverity(expected: HighlightSeverity?) {
    waitUntil("the debugger diagnostic has severity $expected") {
      myFixture.doHighlighting().firstOrNull { "`debugger` statement" in it.description.orEmpty() }?.severity == expected
    }
  }
}
