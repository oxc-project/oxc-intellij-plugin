package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

private const val DOUBLE = "const a = \"b\";\n"
private const val SINGLE = "const a = 'b';\n"

/**
 * Oxlint's server reloads its config by itself when told that one changed, so these check whether
 * Oxfmt's does too, before anything restarts it. A real server is the only way to know.
 */
class OxfmtConfigChangeTest : OxcFixtureTestCase() {

  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    OxlintSettings.getInstance(project).state.configurationMode = ConfigurationMode.DISABLED
    myFixture.testDataPath = "src/test/testData/oxfmt/formatting"
    myFixture.copyDirectoryToProject("nested-config/node_modules", "node_modules")
    file = myFixture.addFileToProject("index.js", DOUBLE).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
  }

  fun testOxfmtrcJsonAppliesWithoutARestart() = checkLifecycle(".oxfmtrc.json") { """{ "singleQuote": $it }""" }

  fun testOxfmtrcJsoncAppliesWithoutARestart() = checkLifecycle(".oxfmtrc.jsonc") { """{ "singleQuote": $it }""" }

  fun testTypeScriptConfigAppliesWithoutARestart() =
    checkLifecycle("oxfmt.config.ts") { """export default { singleQuote: $it };""" }

  fun testModuleTypeScriptConfigAppliesWithoutARestart() =
    checkLifecycle("oxfmt.config.mts") { """export default { singleQuote: $it };""" }

  fun testEditorConfigAppliesWithoutARestart() {
    val indented = myFixture.addFileToProject("indented.js", "function a() {\n  b();\n}\n").virtualFile
    myFixture.configureFromExistingVirtualFile(indented)
    waitForFormat(indented, "function a() {\n  b();\n}\n")
    val client = client()

    createFile(".editorconfig", "root = true\n[*]\nindent_size = 4\nindent_style = space\n")

    waitForFormat(indented, "function a() {\n    b();\n}\n")
    assertSame("The server should not have been restarted", client, client())
  }

  fun testPrettierIgnoreAppliesWithoutARestart() {
    waitForFormat(file, DOUBLE)
    val client = client()
    createFile(".oxfmtrc.json", """{ "singleQuote": true }""")
    waitForFormat(file, SINGLE)
    writeDocument(DOUBLE)

    createFile(".prettierignore", "index.js\n")

    waitForFormat(file, DOUBLE)
    assertSame("The server should not have been restarted", client, client())
  }

  fun testConfigPathAppliesWithoutARestart() {
    useConfigPath("configs/custom.json")
    val config = createFile("configs/custom.json", """{ "singleQuote": false }""")
    waitForFormat(file, DOUBLE)
    val client = client()

    writeFile(config, """{ "singleQuote": true }""")

    waitForFormat(file, SINGLE)
    assertSame("The server should not have been restarted", client, client())
  }

  fun testConfigPathOutsideTheWorkspaceRestartsTheServer() {
    val folder = FileUtil.createTempDirectory("oxc-outside", null, true).toPath()
    val outside = Files.writeString(folder.resolve("custom.json"), """{ "singleQuote": false }""")
    useConfigPath(outside.toString())
    waitForFormat(file, DOUBLE)
    val client = client()

    Files.writeString(outside, """{ "singleQuote": true }""")
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outside)!!.refresh(false, false)

    waitForFormat(file, SINGLE)
    assertNotSame(client, client())
  }

  /** Changing nothing but the mode's path still has to reach the server. */
  private fun useConfigPath(path: String) {
    val settings = OxfmtSettings.getInstance(project)
    settings.update(settings.toData().copy(
      configurationMode = ConfigurationMode.MANUAL,
      configPath = path,
    ))
  }

  private fun checkLifecycle(name: String, content: (Boolean) -> String) {
    waitForFormat(file, DOUBLE)
    val client = client()

    val config = createFile(name, content(true))
    waitForFormat(file, SINGLE)

    writeFile(config, content(false))
    writeDocument(SINGLE)
    waitForFormat(file, DOUBLE)

    // Single quotes again first, because the defaults give the same double quotes as `false`, so only a
    // change back to them shows that the server saw the config go.
    writeFile(config, content(true))
    waitForFormat(file, SINGLE)
    WriteAction.runAndWait<Exception> { config.delete(this) }
    waitForFormat(file, DOUBLE)

    assertSame("The server should not have been restarted", client, client())
  }

  /** Formats until the result is right, and leaves the document as it started so that it can be tried again. */
  private fun waitForFormat(target: VirtualFile, expected: String) {
    val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(target)!!
    val original = document.text
    var last = ""
    try {
      waitUntil("formatting gives $expected") {
        runFormat(target)
        last = document.text
        if (last != expected) WriteAction.runAndWait<Exception> { document.setText(original) }
        last == expected
      }
    }
    catch (error: AssertionError) {
      throw AssertionError("Last result: $last", error)
    }
  }

  private fun writeDocument(text: String) {
    val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)!!
    WriteAction.runAndWait<Exception> { document.setText(text) }
  }

  private fun runFormat(target: VirtualFile) {
    val call = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, target) }
    }
    PlatformTestUtil.waitForFuture(call, 60_000)
  }

  private fun createFile(path: String, content: String): VirtualFile =
    myFixture.addFileToProject(path, content).virtualFile

  private fun writeFile(target: VirtualFile, content: String) {
    WriteAction.runAndWait<Exception> { target.setBinaryContent(content.toByteArray()) }
  }

  private fun client(): LspClient {
    waitUntil("one server is running") {
      LspClientManager.getInstance(project).getClients(OxfmtLspIntegrationProvider::class.java).size == 1
    }
    return LspClientManager.getInstance(project).getClients(OxfmtLspIntegrationProvider::class.java).single()
  }
}
