package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtCodeStyleSettingsModifier
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigFormat
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigLocation
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigLoader
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigResult
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtOverlayTracker
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.locateOxfmtConfig
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleNotice
import com.github.oxc.project.oxcintellijplugin.testutil.FakePackages
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.untrustProject
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.application.options.CodeStyle
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * JavaScript and TypeScript configs, which the overlay reads by running them with the project's Node. A
 * test whose project has a `vite-plus` package uses a fake one, whose `resolveConfig` only imports the
 * config, so the server's rules are what's tested here. `VitePlusRealServerTest` reads a real one.
 */
class OxfmtJsConfigTest : OxcFixtureTestCase() {

  /** What the loader sends. Each notice goes to one collector, and this is the only one. */
  private val notices = CopyOnWriteArrayList<OxfmtCodeStyleNotice>()

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  override fun setUp() {
    super.setUp()
    // The JavaScript plugin only looks in node_modules next to a package.json.
    myFixture.addFileToProject("package.json", """{ "name": "app" }""")
    settings.state.preferOxfmtCodeStyleSettings = true
    val collector = CoroutineScope(Dispatchers.Default).launch {
      OxfmtJsConfigLoader.getInstance(project).notices.collect { notices += it }
    }
    Disposer.register(testRootDisposable) { collector.cancel() }
  }

  fun testTypeScriptConfigIsReadThroughNode() {
    myFixture.addFileToProject("oxfmt.config.ts", "const width: number = 6;\nexport default { tabWidth: width, singleQuote: true };\n")
    val file = myFixture.addFileToProject("index.js", "")

    val overlay = waitForOverlay(file)

    assertEquals(6, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
    assertFalse(overlay.getCustomSettings(JSCodeStyleSettings::class.java).USE_DOUBLE_QUOTES)
    assertEmpty(notices)
  }

  fun testManualJavaScriptConfigIsReadThroughNode() {
    myFixture.addFileToProject("configs/team.mjs", "export default { tabWidth: 5 };\n")
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = "configs/team.mjs"

    val overlay = waitForOverlay(myFixture.addFileToProject("index.js", ""))

    assertEquals(5, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  /** Through the platform, so this covers the refresh that comes once Node has read the config. */
  fun testOverlayArrivesAndFollowsAChange() {
    CodeStyle.dropTemporarySettings(project)
    val config = myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 3 };\n").virtualFile
    val file = myFixture.addFileToProject("index.js", "")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    waitUntil("the indent is 3") { CodeStyle.getIndentOptions(file).INDENT_SIZE == 3 }

    WriteAction.runAndWait<Exception> { config.setBinaryContent("export default { tabWidth: 5 };\n".toByteArray()) }

    waitUntil("the indent is 5") { CodeStyle.getIndentOptions(file).INDENT_SIZE == 5 }
  }

  fun testFailureIsSentOnceUntilTheConfigChanges() {
    val config = myFixture.addFileToProject("oxfmt.config.ts", "throw new Error('broken config');\n").virtualFile
    val file = myFixture.addFileToProject("index.js", "")

    waitUntil("a failure is sent") { runModifier(file); notices.isNotEmpty() }
    repeat(3) { assertNull(runModifier(file)) }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val failure = notices.single() as OxfmtCodeStyleNotice.Failed
    assertEquals("oxfmt.config.ts", failure.configName)
    assertTrue(failure.message, "broken config" in failure.message)

    WriteAction.runAndWait<Exception> { config.setBinaryContent("throw new Error('still broken');\n".toByteArray()) }
    waitUntil("the changed config's failure is sent") { runModifier(file); notices.size == 2 }
    assertTrue("still broken" in (notices.last() as OxfmtCodeStyleNotice.Failed).message)
  }

  /** A failure's details have the whole error, such as its stack. */
  fun testFailureDetailsHaveTheStack() {
    myFixture.addFileToProject("oxfmt.config.ts", "throw new Error('broken config');\n")
    val file = myFixture.addFileToProject("index.js", "").virtualFile

    val location = locate(file)
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxfmtJsConfigResult> {
      runBlocking { OxfmtJsConfigLoader.getInstance(project).load(location) }
    }
    val result = PlatformTestUtil.waitForFuture(call, 60_000) as OxfmtJsConfigResult.Failed

    assertEquals("broken config", result.message)
    assertTrue(result.details, result.details.startsWith("Error: broken config\n"))
    assertTrue(result.details, "oxfmt.config.ts:1" in result.details)
  }

  fun testUntrustedProjectRunsNothing() {
    val config = myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 4 };\n").virtualFile
    val file = myFixture.addFileToProject("index.js", "")
    untrustProject(project, testRootDisposable)

    waitUntil("the untrusted notice is sent") { runModifier(file); notices.isNotEmpty() }
    // Another config doesn't ask again in this session.
    WriteAction.runAndWait<Exception> { config.setBinaryContent("export default { tabWidth: 8 };\n".toByteArray()) }
    repeat(3) { assertNull(runModifier(file)); Thread.sleep(100); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    val untrusted = notices.single() as OxfmtCodeStyleNotice.Untrusted
    assertEquals("oxfmt.config.ts", untrusted.configName)
  }

  /**
   * Settings | Trusted Locations trusts a folder and names no project, so nothing told the overlay, which
   * kept Untrusted until a file was reopened.
   */
  fun testTrustFromTrustedLocationsRefreshesTheOverlay() {
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 4 };\n")
    val file = myFixture.addFileToProject("index.js", "")
    untrustProject(project, testRootDisposable)
    waitUntil("the untrusted notice is sent") { runModifier(file); notices.isNotEmpty() }
    val tracker = OxfmtOverlayTracker.getInstance(project)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val before = tracker.modificationCount

    // The project counts as trusted again without an event, as after Trusted Locations stores the folder.
    System.clearProperty(TrustedProjects.TRUST_HEADLESS_DISABLED_PROPERTY)
    assertTrue(TrustedProjects.isProjectTrusted(project))
    ApplicationManager.getApplication().messageBus.syncPublisher(TrustedProjectsListener.TOPIC)
      .onProjectTrusted(TrustedProjectsLocator.locateProject(Path.of(project.basePath!!), null))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertTrue("the overlay was refreshed", tracker.modificationCount > before)
    assertEquals(4, waitForOverlay(file).getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  /** Node installed at the path the project already names, which leaves the interpreter's name as it was. */
  fun testRetryingNodeFailuresReadsTheConfigAgain() {
    if (SystemInfo.isWindows || WslTesting.enabled) return
    val realNode = NodeJsInterpreterManager.getInstance(project).interpreter!!.referenceName
    val node = File(myFixture.tempDirPath, "later/node")
    NodeJsInterpreterManager.getInstance(project).setInterpreterRef(NodeJsInterpreterRef.create(node.path), testRootDisposable)
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 7 };\n")
    val file = myFixture.addFileToProject("index.js", "")
    waitUntil("a failure is sent") { runModifier(file); notices.isNotEmpty() }

    // Still no Node: read again, but the same failure isn't sent again.
    OxfmtJsConfigLoader.getInstance(project).retryNodeFailures()
    repeat(5) { assertNull(runModifier(file)); Thread.sleep(200); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertEquals(1, notices.size)

    node.parentFile.mkdirs()
    Files.createSymbolicLink(node.toPath(), File(realNode).toPath())
    assertNull("The failure is cached until Node is retried", runModifier(file))
    OxfmtJsConfigLoader.getInstance(project).retryNodeFailures()

    assertEquals(7, waitForOverlay(file).getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  /** A stand-in for an older Node.js, which answers as the script does when it can't load TypeScript. */
  fun testOldNodeSaysWhichVersionItIs() {
    if (SystemInfo.isWindows || WslTesting.enabled) return
    val node = File(myFixture.tempDirPath, "old-node/node")
    node.parentFile.mkdirs()
    node.writeText("""
      #!/bin/sh
      if [ "${'$'}1" = "--version" ]; then echo v20.0.0; exit 0; fi
      echo '{"kind":"node-version","index":0,"version":"v20.0.0","message":"Unknown file extension \".ts\""}'
    """.trimIndent() + "\n")
    node.setExecutable(true)
    NodeJsInterpreterManager.getInstance(project).setInterpreterRef(NodeJsInterpreterRef.create(node.path), testRootDisposable)
    myFixture.addFileToProject("oxfmt.config.ts", "export default {};\n")
    val file = myFixture.addFileToProject("index.js", "")

    waitUntil("a failure is sent") { runModifier(file); notices.isNotEmpty() }

    val failure = notices.single() as OxfmtCodeStyleNotice.Failed
    assertEquals("TypeScript configs need Node.js 22.18.0 or later, and the project's Node.js is v20.0.0.",
                 failure.message)
  }

  fun testNativeBinaryCantReadAJavaScriptConfig() {
    val binary = myFixture.addFileToProject("bin/oxfmt", "not a script").virtualFile
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = binary.path
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 4 };\n")
    val file = myFixture.addFileToProject("index.js", "")

    assertEquals(OxfmtConfigFormat.UNREADABLE, locate(file.virtualFile).format)
    assertNull(runModifier(file))
    assertEmpty(notices)
  }

  fun testVitePlusReadsFmtFromViteConfig() {
    FakePackages.vitePlus(myFixture)
    myFixture.addFileToProject("vite.config.ts", "export default { lint: {}, fmt: { tabWidth: 7 } };\n")
    val file = myFixture.addFileToProject("src/index.js", "")

    val overlay = waitForOverlay(file)

    assertEquals(7, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
    val call = File(myFixture.tempDirPath, "node_modules/vite-plus/calls.log").readText().trim()
    assertTrue(call, call.endsWith("vite.config.ts build 1.5.0"))
  }

  /** Under `vp`, the server only looks for `vite.config.*`, so Oxfmt's own configs don't count. */
  fun testVitePlusIgnoresOxfmtConfigs() {
    FakePackages.vitePlus(myFixture)
    myFixture.addFileToProject(".oxfmtrc.json", """{ "tabWidth": 3 }""")
    myFixture.addFileToProject("src/.oxfmtrc.json", """{ "tabWidth": 3 }""")
    val viteConfig = myFixture.addFileToProject("vite.config.mjs", "export default { fmt: {} };\n").virtualFile

    val location = locate(myFixture.addFileToProject("src/index.js", "").virtualFile)

    assertEquals(viteConfig, location.file)
    assertEquals(OxfmtConfigFormat.VITE_PLUS, location.format)
  }

  fun testVitePlusTakesTheFirstNameInVitesOrder() {
    FakePackages.vitePlus(myFixture)
    myFixture.addFileToProject("vite.config.ts", "export default { fmt: {} };\n")
    val first = myFixture.addFileToProject("vite.config.mjs", "export default { fmt: {} };\n").virtualFile

    assertEquals(first, locate(myFixture.addFileToProject("index.js", "").virtualFile).file)
  }

  /** The server skips a `vite.config.*` with no `fmt` and keeps looking above it. */
  fun testVitePlusSkipsAViteConfigWithoutFmt() {
    FakePackages.vitePlus(myFixture, "app/node_modules/vite-plus")
    myFixture.addFileToProject("app/package.json", """{ "name": "app" }""")
    myFixture.addFileToProject("app/vite.config.mjs", "export default { lint: {} };\n")
    myFixture.addFileToProject("vite.config.mjs", "export default { fmt: { tabWidth: 4 } };\n")

    val overlay = waitForOverlay(myFixture.addFileToProject("app/index.js", ""))

    assertEquals(4, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  fun testVitePlusWithoutFmtIsLikeNoConfig() {
    FakePackages.vitePlus(myFixture)
    myFixture.addFileToProject("vite.config.mjs", "export default { lint: {} };\n")
    val file = myFixture.addFileToProject("index.js", "")

    assertNull(runModifier(file))
    assertEmpty(notices)
  }

  private fun locate(file: VirtualFile): OxfmtConfigLocation =
    ReadAction.compute<OxfmtConfigLocation?, Exception> { locateOxfmtConfig(project, file) }!!

  /** The first ask starts Node, so this asks until the overlay is there. */
  private fun waitForOverlay(file: PsiFile): TransientCodeStyleSettings {
    var overlay: TransientCodeStyleSettings? = null
    waitUntil("the overlay is there", timeout = 60.seconds) { overlay = runModifier(file); overlay != null }
    return overlay!!
  }

  /** Returns the changed copy, or null if the modifier left it alone. */
  private fun runModifier(file: PsiFile): TransientCodeStyleSettings? =
    ReadAction.compute<TransientCodeStyleSettings?, Exception> {
      val copy = TransientCodeStyleSettings(file.virtualFile, project, CodeStyle.getSettings(project))
      copy.takeIf { OxfmtCodeStyleSettingsModifier().modifySettings(it, file) }
    }
}
