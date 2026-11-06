package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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

  fun testJsoncConfigAppliesWithoutARestart() {
    checkConfigLifecycle(".oxlintrc.jsonc") { level -> """{ /* comment */ "rules": { "no-debugger": "$level" } }""" }
  }

  fun testTypeScriptConfigAppliesWithoutARestart() {
    checkConfigLifecycle("oxlint.config.ts") { level -> """export default { rules: { "no-debugger": "$level" } };""" }
  }

  fun testModuleTypeScriptConfigAppliesWithoutARestart() {
    checkConfigLifecycle("oxlint.config.mts") { level -> """export default { rules: { "no-debugger": "$level" } };""" }
  }

  fun testExtendedBaseFileAppliesWithoutARestart() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.findFileInTempDir("index.js")
    val base = createConfig(file.parent, RULES_OFF, "base.json")
    createConfig(file.parent, """{ "extends": ["./base.json"] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitForDebuggerSeverity(null)
    val client = client()

    writeConfig(base, RULES_ERROR)
    waitForDebuggerSeverity(HighlightSeverity.ERROR)

    assertSame("The server should not have been restarted", client, client())
  }

  fun testExtendedBaseFileOutsideTheWorkspaceAppliesAfterARestart() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.findFileInTempDir("index.js")
    val outside = createOutsideFile("base.json", RULES_OFF)
    createConfig(file.parent, """{ "extends": ["${FileUtil.toSystemIndependentName(outside)}"] }""")
    myFixture.configureFromExistingVirtualFile(file)
    waitForDebuggerSeverity(null)
    val client = client()

    Files.writeString(Path.of(outside), RULES_ERROR)
    LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(OxlintLspIntegrationProvider::class.java)

    waitForDebuggerSeverity(HighlightSeverity.ERROR)
    assertNotSame(client, client())
  }

  fun testRelativeConfigPathAppliesWithoutARestart() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.findFileInTempDir("index.js")
    val config = createConfig(file.parent, RULES_OFF, "custom.json")
    useConfigPath("custom.json")

    myFixture.configureFromExistingVirtualFile(file)
    waitForDebuggerSeverity(null)
    val client = client()

    writeConfig(config, RULES_ERROR)
    waitForDebuggerSeverity(HighlightSeverity.ERROR)

    assertSame("The server should not have been restarted", client, client())
  }

  fun testAbsoluteConfigPathInTheWorkspaceAppliesWithoutARestart() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.findFileInTempDir("index.js")
    val config = createConfig(file.parent, RULES_OFF, "custom.json")
    useConfigPath(config.path)

    myFixture.configureFromExistingVirtualFile(file)
    waitForDebuggerSeverity(null)
    val client = client()

    writeConfig(config, RULES_ERROR)
    waitForDebuggerSeverity(HighlightSeverity.ERROR)

    assertSame("The server should not have been restarted", client, client())
  }

  fun testConfigPathOutsideTheWorkspaceRestartsWhenEditedOutsideTheIde() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.findFileInTempDir("index.js")
    val outside = createOutsideFile("custom.json", RULES_OFF)
    useConfigPath(outside)

    myFixture.configureFromExistingVirtualFile(file)
    waitForDebuggerSeverity(null)
    val client = client()

    Files.writeString(Path.of(outside), RULES_ERROR)
    LocalFileSystem.getInstance().refreshNioFiles(listOf(Path.of(outside)))

    waitForDebuggerSeverity(HighlightSeverity.ERROR)
    assertNotSame(client, client())
  }

  fun testDefaultConfigIsIgnoredWhileAConfigPathIsSet() {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.findFileInTempDir("index.js")
    val config = createConfig(file.parent, RULES_OFF, "custom.json")
    useConfigPath("custom.json")
    myFixture.configureFromExistingVirtualFile(file)
    waitForDebuggerSeverity(null)

    createConfig(file.parent, RULES_ERROR)
    waitForDebuggerSeverity(null, holdFor = 3.seconds)

    // Proves that the server was still listening, and to which file.
    writeConfig(config, """{ "rules": { "no-debugger": "warn" } }""")
    waitForDebuggerSeverity(HighlightSeverity.WARNING)
  }

  private fun checkConfigLifecycle(name: String, content: (String) -> String) {
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.configureFromTempProjectFile("index.js").virtualFile
    waitForDebuggerSeverity(HighlightSeverity.WARNING)
    val client = client()

    val config = createConfig(file.parent, content("off"), name)
    waitForDebuggerSeverity(null)

    writeConfig(config, content("error"))
    waitForDebuggerSeverity(HighlightSeverity.ERROR)

    WriteAction.runAndWait<Exception> { config.delete(this) }
    waitForDebuggerSeverity(HighlightSeverity.WARNING)

    assertSame("The server should not have been restarted", client, client())
  }

  private fun useConfigPath(path: String) {
    val settings = OxlintSettings.getInstance(project)
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = path
  }

  /** Written with plain file calls, so that the IDE hasn't loaded the file. */
  private fun createOutsideFile(name: String, content: String): String {
    val folder = FileUtil.createTempDirectory("oxc-outside", null, true).toPath()
    return Files.writeString(folder.resolve(name), content).toString()
  }

  private fun createConfig(folder: VirtualFile, content: String, name: String = ".oxlintrc.json"): VirtualFile =
    WriteAction.computeAndWait<VirtualFile, Exception> {
      folder.createChildData(this, name).also { it.setBinaryContent(content.toByteArray()) }
    }

  private fun writeConfig(config: VirtualFile, content: String) {
    WriteAction.runAndWait<Exception> { config.setBinaryContent(content.toByteArray()) }
  }

  private fun client(): LspClient =
    LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java).single()

  private fun debuggerSeverity(): HighlightSeverity? =
    myFixture.doHighlighting().firstOrNull { "`debugger` statement" in it.description.orEmpty() }?.severity

  private fun waitForDebuggerSeverity(expected: HighlightSeverity?, holdFor: Duration = Duration.ZERO) {
    waitUntil("the debugger diagnostic has severity $expected") { debuggerSeverity() == expected }
    val deadline = TimeSource.Monotonic.markNow() + holdFor
    while (deadline.hasNotPassedNow()) {
      assertEquals(expected, debuggerSeverity())
      Thread.sleep(100)
    }
  }

  private companion object {
    const val RULES_OFF = """{ "rules": { "no-debugger": "off" } }"""
    const val RULES_ERROR = """{ "rules": { "no-debugger": "error" } }"""
  }
}
