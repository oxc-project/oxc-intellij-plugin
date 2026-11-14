package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.extensions.configureByFileAndCheckLanguageServerHighlighting
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import java.io.File
import java.nio.file.Files

/**
 * Runs a real Oxlint server from an executable that wraps the Node script, so that it's portable. Node
 * can't run the wrapper, so diagnostics only appear if it was started directly. The project has no
 * `node_modules`, as with most uses of a native binary. Needs Node on PATH and `npm ci` run in the
 * fixture folder. [com.github.oxc.project.oxcintellijplugin.windows.WslNativeBinaryTest] covers WSL.
 */
@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class NativeBinaryTest : OxcFixtureTestCase() {

  override fun shouldRunTest(): Boolean = !WslTesting.enabled && super.shouldRunTest()

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting/no-config"
  }

  fun testManualBinaryThatIsNotANodeScriptRunsDirectly() {
    useManualBinary(createWrapper())

    myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
  }

  fun testManualBinaryThatDoesNotExistStartsNoServer() {
    useManualBinary(File(FileUtil.createTempDirectory("oxc-native", null, true), "missing").path)

    myFixture.configureByFile("index.js")
    repeat(20) {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      Thread.sleep(100)
    }

    val servers = LspClientManager.getInstance(project).getClients(OxlintLspIntegrationProvider::class.java)
    assertTrue(servers.none { it.state == LspServerState.Running })
  }

  private fun useManualBinary(path: String) {
    val settings = OxlintSettings.getInstance(project)
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = path
  }

  /** The folder name has a space, because a wrapper on Windows runs through `cmd.exe`. */
  private fun createWrapper(): String {
    val folder = FileUtil.createTempDirectory("oxc native", null, true).toPath()
    val script = File("src/test/testData/oxlint/highlighting/no-config/node_modules/oxlint/bin/oxlint").absolutePath
    if (SystemInfo.isWindows) {
      return Files.writeString(folder.resolve("oxlint.cmd"), "@node \"$script\" %*\r\n").toString()
    }

    val wrapper = Files.writeString(folder.resolve("oxlint"), "#!/bin/sh\nexec node \"$script\" \"\$@\"\n")
    assertTrue(wrapper.toFile().setExecutable(true))
    return wrapper.toString()
  }
}
