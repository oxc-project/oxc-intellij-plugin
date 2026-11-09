package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtCodeStyleSettingsModifier
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigFormat
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigLocation
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigLoader
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigResult
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtOverlayTracker
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.importOxfmtCodeStyle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.locateOxfmtConfig
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.frontend.oxfmt.OxfmtConfigNotifier
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
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
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/** The frontend shows these notifications, in its own group. */
private const val NOTIFICATION_GROUP = "Oxc"

/**
 * JavaScript and TypeScript configs, which the overlay and Import read by running them with the project's
 * Node. A test whose project has a `vite-plus` package uses a fake one, whose `resolveConfig` only imports
 * the config, so the server's rules are what's tested here. `VitePlusRealServerTest` reads a real one.
 */
class OxfmtJsConfigTest : OxcFixtureTestCase() {

  private val notifications = CopyOnWriteArrayList<Notification>()

  private val settings: OxfmtSettings
    get() = OxfmtSettings.getInstance(project)

  override fun setUp() {
    super.setUp()
    // The JavaScript plugin only looks in node_modules next to a package.json.
    myFixture.addFileToProject("package.json", """{ "name": "app" }""")
    settings.state.preferOxfmtCodeStyleSettings = true
    project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == NOTIFICATION_GROUP) notifications += notification
      }
    })
    // The frontend listens once its startup activity runs, which a test project may not have reached yet.
    project.service<OxfmtConfigNotifier>()
  }

  fun testTypeScriptConfigIsReadThroughNode() {
    myFixture.addFileToProject("oxfmt.config.ts", "const width: number = 6;\nexport default { tabWidth: width, singleQuote: true };\n")
    val file = myFixture.addFileToProject("index.js", "")

    val overlay = waitForOverlay(file)

    assertEquals(6, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
    assertFalse(overlay.getCustomSettings(JSCodeStyleSettings::class.java).USE_DOUBLE_QUOTES)
    assertEmpty(notifications)
  }

  fun testManualJavaScriptConfigIsReadThroughNode() {
    myFixture.addFileToProject("configs/team.mjs", "export default { tabWidth: 5 };\n")
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = "configs/team.mjs"

    val overlay = waitForOverlay(myFixture.addFileToProject("index.js", ""))

    assertEquals(5, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  fun testImportReadsATypeScriptConfig() {
    CodeStyle.dropTemporarySettings(project)
    myFixture.addFileToProject("oxfmt.config.mts", "export default { tabWidth: 9 };\n")
    val file = myFixture.addFileToProject("index.js", "").virtualFile

    assertEquals(OxfmtImportResult.IMPORTED, runImport(file))

    assertEquals(9, CodeStyle.getSettings(project).getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
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

  fun testFailureIsShownOnceUntilTheConfigChanges() {
    val config = myFixture.addFileToProject("oxfmt.config.ts", "throw new Error('broken config');\n").virtualFile
    val file = myFixture.addFileToProject("index.js", "")

    waitUntil("a failure is shown") { runModifier(file); notifications.isNotEmpty() }
    repeat(3) { assertNull(runModifier(file)) }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val failure = notifications.single()
    assertEquals("Oxfmt couldn't read oxfmt.config.ts", failure.title)
    assertTrue(failure.content, "broken config" in failure.content)
    assertEquals(listOf("Show Details", "Edit Configuration File"), failure.actions.map { it.templateText })

    // The backend opens it, through the frontend.
    Notification.fire(failure, failure.actions.last(), null)
    waitUntil("the config is open") { FileEditorManager.getInstance(project).isFileOpen(config) }

    WriteAction.runAndWait<Exception> { config.setBinaryContent("throw new Error('still broken');\n".toByteArray()) }
    waitUntil("the changed config's failure is shown") { runModifier(file); notifications.size == 2 }
    assertTrue("still broken" in notifications.last().content)
  }

  /** Show Details has the whole error, because the notification only has its first lines. */
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

  fun testImportShowsTheFailureAgain() {
    myFixture.addFileToProject("oxfmt.config.ts", "export default 3;\n")
    val file = myFixture.addFileToProject("index.js", "")
    waitUntil("a failure is shown") { runModifier(file); notifications.isNotEmpty() }

    assertEquals(OxfmtImportResult.CONFIG_NOT_LOADED, runImport(file.virtualFile))

    waitUntil("the failure is shown again, because Import asks") { notifications.size == 2 }
    assertTrue(notifications.last().content, "must have a default export that is an object" in notifications.last().content)
    // Import read it again, and only Import showed it.
    repeat(5) { runModifier(file); Thread.sleep(200); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertEquals(2, notifications.size)
  }

  /** Import reads a failed config again, so it picks up Node installed where the project already looks. */
  fun testImportReadsAConfigThatFailedForWantOfNode() {
    if (SystemInfo.isWindows || WslTesting.enabled) return
    val realNode = NodeJsInterpreterManager.getInstance(project).interpreter!!.referenceName
    val node = File(myFixture.tempDirPath, "later/node")
    NodeJsInterpreterManager.getInstance(project).setInterpreterRef(NodeJsInterpreterRef.create(node.path), testRootDisposable)
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 7 };\n")
    val file = myFixture.addFileToProject("index.js", "")
    waitUntil("a failure is shown") { runModifier(file); notifications.isNotEmpty() }

    node.parentFile.mkdirs()
    java.nio.file.Files.createSymbolicLink(node.toPath(), File(realNode).toPath())

    assertEquals(OxfmtImportResult.IMPORTED, runImport(file.virtualFile))
  }

  fun testUntrustedProjectRunsNothingAndAsksToTrust() {
    val config = myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 4 };\n").virtualFile
    val file = myFixture.addFileToProject("index.js", "")
    untrustProject(project, testRootDisposable)

    waitUntil("the untrusted notification is shown") { runModifier(file); notifications.isNotEmpty() }
    // Another config doesn't ask again in this session.
    WriteAction.runAndWait<Exception> { config.setBinaryContent("export default { tabWidth: 8 };\n".toByteArray()) }
    repeat(3) { assertNull(runModifier(file)); Thread.sleep(100); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    val untrusted = notifications.single()
    assertEquals("Oxfmt code style needs a trusted project", untrusted.title)
    assertTrue(untrusted.content, "oxfmt.config.ts" in untrusted.content)
    assertEquals(listOf("Trust Project"), untrusted.actions.map { it.templateText })

    assertEquals(OxfmtImportResult.CONFIG_NOT_LOADED, runImport(file.virtualFile))
    waitUntil("it's shown again, because Import asks") { notifications.size == 2 }

    // Trust Project asks the backend, through the frontend.
    Notification.fire(untrusted, untrusted.actions.single(), null)
    waitUntil("the project is trusted") { TrustedProjects.isProjectTrusted(project) }

    val overlay = waitForOverlay(file)
    assertEquals(8, overlay.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
  }

  /**
   * Settings | Trusted Locations trusts a folder and names no project, so nothing told the overlay, which
   * kept Untrusted until a file was reopened.
   */
  fun testTrustFromTrustedLocationsRefreshesTheOverlay() {
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 4 };\n")
    val file = myFixture.addFileToProject("index.js", "")
    untrustProject(project, testRootDisposable)
    waitUntil("the untrusted notification is shown") { runModifier(file); notifications.isNotEmpty() }
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

  fun testNoNodeOffersItsSettings() {
    NodeJsInterpreterManager.getInstance(project)
      .setInterpreterRef(NodeJsInterpreterRef.create("${myFixture.tempDirPath}/missing/node"), testRootDisposable)
    myFixture.addFileToProject("oxfmt.config.ts", "export default {};\n")
    val file = myFixture.addFileToProject("index.js", "")

    waitUntil("a failure is shown") { runModifier(file); notifications.isNotEmpty() }

    assertEquals(listOf("Configure Node.js…", "Edit Configuration File"), notifications.single().actions.map { it.templateText })
  }

  /** Node installed at the path the project already names, which leaves the interpreter's name as it was. */
  fun testRetryingNodeFailuresReadsTheConfigAgain() {
    if (SystemInfo.isWindows || WslTesting.enabled) return
    val realNode = NodeJsInterpreterManager.getInstance(project).interpreter!!.referenceName
    val node = File(myFixture.tempDirPath, "later/node")
    NodeJsInterpreterManager.getInstance(project).setInterpreterRef(NodeJsInterpreterRef.create(node.path), testRootDisposable)
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 7 };\n")
    val file = myFixture.addFileToProject("index.js", "")
    waitUntil("a failure is shown") { runModifier(file); notifications.isNotEmpty() }

    // Still no Node: read again, but the same failure isn't shown again.
    OxfmtJsConfigLoader.getInstance(project).retryNodeFailures()
    repeat(5) { assertNull(runModifier(file)); Thread.sleep(200); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    assertEquals(1, notifications.size)

    node.parentFile.mkdirs()
    java.nio.file.Files.createSymbolicLink(node.toPath(), File(realNode).toPath())
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

    waitUntil("a failure is shown") { runModifier(file); notifications.isNotEmpty() }

    val failure = notifications.single()
    assertEquals("TypeScript configs need Node.js 22.18.0 or later, and the project's Node.js is v20.0.0.",
                 StringUtil.unescapeXmlEntities(failure.content))
    assertEquals(listOf("Configure Node.js…", "Edit Configuration File"), failure.actions.map { it.templateText })
  }

  fun testNativeBinaryCantReadAJavaScriptConfig() {
    val binary = myFixture.addFileToProject("bin/oxfmt", "not a script").virtualFile
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = binary.path
    myFixture.addFileToProject("oxfmt.config.ts", "export default { tabWidth: 4 };\n")
    val file = myFixture.addFileToProject("index.js", "")

    assertEquals(OxfmtConfigFormat.UNREADABLE, locate(file.virtualFile).format)
    assertNull(runModifier(file))
    assertEquals(OxfmtImportResult.CONFIG_NOT_READABLE, runImport(file.virtualFile))
    assertEmpty(notifications)
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

    assertEquals(OxfmtImportResult.CONFIG_NOT_FOUND, runImport(file.virtualFile))
    assertNull(runModifier(file))
    assertEmpty(notifications)
  }

  /** The server calls a manual config without `fmt` an error. */
  fun testVitePlusManualConfigNeedsFmt() {
    FakePackages.vitePlus(myFixture)
    myFixture.addFileToProject("configs/vite.shared.mjs", "export default { lint: {} };\n")
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.configPath = "configs/vite.shared.mjs"

    assertEquals(OxfmtImportResult.CONFIG_NOT_LOADED, runImport(myFixture.addFileToProject("index.js", "").virtualFile))

    waitUntil("the failure is shown") { notifications.isNotEmpty() }
    assertEquals("Expected a `fmt` field in the default export of vite.shared.mjs.", notifications.single().content)
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

  private fun runImport(file: VirtualFile): OxfmtImportResult {
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxfmtImportResult> {
      runBlocking { importOxfmtCodeStyle(project, file) }
    }
    return PlatformTestUtil.waitForFuture(call, 60_000)
  }
}
