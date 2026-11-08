package com.github.oxc.project.oxcintellijplugin.viteplus

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtCodeStyleSettingsModifier
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.application.options.CodeStyle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** Neither config formats the second line as it is, so a format that does nothing never passes. */
private const val SOURCE = "debugger;\nconst a = 'b'\n"

/**
 * A real `vite-plus` project: the servers are `vp lint --lsp` and `vp fmt --lsp`, and they read
 * `vite.config.ts`. `vp` asks the client to watch every `vite.config.*`, so a change applies without a
 * restart. The fixture's `node_modules` is too big to copy for each test, so the fixture's folder is
 * added to the project instead, and `vite.config.ts` is put back after each test.
 *
 * `OXC_TEST_VITEPLUS_FIXTURE` points at another copy of the fixture. The WSL project job installs one
 * inside WSL, so that the project and its `node_modules` are on the same side, as a user's would be.
 */
class VitePlusRealServerTest : OxcFixtureTestCase() {

  private val fixture: Path =
    (System.getenv("OXC_TEST_VITEPLUS_FIXTURE")?.takeIf { it.isNotBlank() } ?: "src/test/testData/viteplus/project")
      .let { Path.of(it).toAbsolutePath() }
  private val viteConfig: Path = fixture.resolve("vite.config.ts")
  private lateinit var originalViteConfig: String
  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    originalViteConfig = Files.readString(viteConfig)
    val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixture)!!
    PsiTestUtil.addContentRoot(myFixture.module, root)
    file = root.findChild("index.js")!!
    myFixture.configureFromExistingVirtualFile(file)
  }

  override fun tearDown() {
    try {
      if (::originalViteConfig.isInitialized) {
        Files.writeString(viteConfig, originalViteConfig)
        LocalFileSystem.getInstance().refreshNioFiles(listOf(viteConfig))
      }
      if (::file.isInitialized) {
        WriteAction.runAndWait<Exception> { FileDocumentManager.getInstance().getDocument(file)!!.setText(SOURCE) }
        FileDocumentManager.getInstance().saveAllDocuments()
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testServersRunThroughVp() {
    waitForDebuggerSeverity(HighlightSeverity.ERROR)

    assertEquals(listOf("lint", "--lsp"), launch(OxlintLspIntegrationProvider::class.java).parameters)
    formatUntil("const a = 'b';")
    assertEquals(listOf("fmt", "--lsp"), launch(OxfmtLspIntegrationProvider::class.java).parameters)
    assertTrue(launch(OxlintLspIntegrationProvider::class.java).executable.replace('\\', '/').endsWith("vite-plus/bin/vp"))
  }

  fun testLintConfigChangeAppliesWithoutARestart() {
    waitForDebuggerSeverity(HighlightSeverity.ERROR)
    val client = client(OxlintLspIntegrationProvider::class.java)

    writeViteConfig(originalViteConfig.replace("'eslint/no-debugger': 'error'", "'eslint/no-debugger': 'off'"))

    waitForDebuggerSeverity(null)
    assertSame("The server should not have been restarted", client, client(OxlintLspIntegrationProvider::class.java))
  }

  fun testFmtConfigChangeAppliesWithoutARestart() {
    formatUntil("const a = 'b';")
    val client = client(OxfmtLspIntegrationProvider::class.java)

    writeViteConfig(originalViteConfig.replace("singleQuote: true", "singleQuote: false"))

    formatUntil("const a = \"b\";")
    assertSame("The server should not have been restarted", client, client(OxfmtLspIntegrationProvider::class.java))
  }

  /** Node runs the real `vite-plus`'s `resolveConfig` on `vite.config.ts`, which is what `vp fmt` reads. */
  fun testOverlayReadsFmtFromViteConfig() {
    OxfmtSettings.getInstance(project).state.preferOxfmtCodeStyleSettings = true
    val psiFile = PsiManager.getInstance(project).findFile(file)!!

    waitUntil("the overlay uses single quotes", timeout = 60.seconds) {
      val overlay = ReadAction.compute<TransientCodeStyleSettings?, Exception> {
        val copy = TransientCodeStyleSettings(file, project, CodeStyle.getSettings(project))
        copy.takeIf { OxfmtCodeStyleSettingsModifier().modifySettings(it, psiFile) }
      }
      overlay?.getCustomSettings(JSCodeStyleSettings::class.java)?.USE_DOUBLE_QUOTES == false
    }
  }

  /** Written as an editor would, so the platform sends the server the change it asked to watch. */
  private fun writeViteConfig(content: String) {
    val config = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(viteConfig)!!
    WriteAction.runAndWait<Exception> { config.setBinaryContent(content.toByteArray()) }
  }

  private fun launch(provider: Class<out ToolLspIntegrationProvider>) =
    (client(provider).descriptor as ToolLspClientDescriptor).launch

  private fun client(provider: Class<out ToolLspIntegrationProvider>): LspClient {
    waitUntil("one ${provider.simpleName} server is running") {
      LspClientManager.getInstance(project).getClients(provider).size == 1
    }
    return LspClientManager.getInstance(project).getClients(provider).single()
  }

  private fun debuggerSeverity(): HighlightSeverity? =
    myFixture.doHighlighting().firstOrNull { "`debugger` statement" in it.description.orEmpty() }?.severity

  /** No diagnostic also looks like the server not having answered yet, so this waits for a client first. */
  private fun waitForDebuggerSeverity(expected: HighlightSeverity?) {
    client(OxlintLspIntegrationProvider::class.java)
    waitUntil("the debugger diagnostic has severity $expected", timeout = 60.seconds) {
      debuggerSeverity() == expected
    }
  }

  /** Formats until the second line is right, putting the document back each time so that it can be tried again. */
  private fun formatUntil(expectedLine: String) {
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    var last = ""
    try {
      waitUntil("formatting gives $expectedLine", timeout = 60.seconds) {
        val call = ApplicationManager.getApplication().executeOnPooledThread {
          runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, file) }
        }
        PlatformTestUtil.waitForFuture(call, 60_000)
        last = document.text
        WriteAction.runAndWait<Exception> { document.setText(SOURCE) }
        expectedLine in last.lines()
      }
    }
    catch (error: AssertionError) {
      throw AssertionError("Last result: $last", error)
    }
  }
}
