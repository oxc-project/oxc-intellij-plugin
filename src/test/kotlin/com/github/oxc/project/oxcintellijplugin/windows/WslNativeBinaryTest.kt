package com.github.oxc.project.oxcintellijplugin.windows

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.extensions.configureByFileAndCheckLanguageServerHighlighting
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting.onPooledThread
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.wsl.WslPath
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.ExperimentalPathApi

/**
 * A manual binary at a path into WSL has to run inside that distribution, and be given WSL paths,
 * whether the project is on the Windows side or stored inside WSL. It's a shell script wrapping the
 * Node script, and Node can't run it, so diagnostics only appear if it was started directly. See
 * [WslTesting] for how to enable this test.
 */
@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class WslNativeBinaryTest : OxcFixtureTestCase() {

  override fun shouldRunTest(): Boolean = WslTesting.enabled && super.shouldRunTest()

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting/no-config"
  }

  @OptIn(ExperimentalPathApi::class)
  fun testManualBinaryInWslRunsInsideIt() {
    val folder = Path.of("\\\\wsl.localhost\\${WslTesting.distributionId}\\tmp\\oxc-native-${System.nanoTime()}")
    folder.createDirectories()
    try {
      val settings = OxlintSettings.getInstance(project)
      settings.state.configurationMode = ConfigurationMode.MANUAL
      settings.state.binaryPath = createWrapper(folder)

      myFixture.configureByFileAndCheckLanguageServerHighlighting("index.expected.js")
    }
    finally {
      folder.deleteRecursively()
    }
  }

  private fun createWrapper(folder: Path): String {
    val script = File("src/test/testData/oxlint/highlighting/no-config/node_modules/oxlint/bin/oxlint").toPath().toAbsolutePath()
    val wrapper = folder.resolve("oxlint")
    val wslPath = WslPath.parseWindowsUncPath(wrapper.toString())!!.linuxPath

    onPooledThread {
      val distribution = WslPath.getDistributionByWindowsUncPath(wrapper.toString())!!
      val wslScript = distribution.getWslPath(script)
      Files.writeString(wrapper, "#!/bin/sh\nexec \"${WslTesting.nodePath}\" \"$wslScript\" \"\$@\"\n")

      val chmod = GeneralCommandLine("wsl.exe", "-d", WslTesting.distributionId!!, "--exec", "chmod", "755", wslPath)
      assertEquals(0, CapturingProcessHandler(chmod).runProcess(30_000).exitCode)
    }
    return wrapper.toString()
  }
}
