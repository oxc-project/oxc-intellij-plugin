package com.github.oxc.project.oxcintellijplugin.packagemanagers

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigFormat
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigLocation
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigLoader
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigResult
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.locateOxfmtConfig
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.backend.tool.ServerLaunch
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolPackage
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.PathUtil
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

private const val ROOT_FORMATTED = "debugger;\nconst a = 'b';\n"
private const val NESTED_FORMATTED = "debugger;\nconst a = \"b\";\n"
private const val VITE_FORMATTED = "debugger\nconst a = \"b\"\n"

/**
 * The same workspace installed by each package manager, with real servers. Each subclass is one manager,
 * so that a failure names it. The workspace has three folders, and each test opens one of them alone:
 * - the root, with Oxlint 1.88.0 and Oxfmt 0.77.0, `no-debugger` as an error and single quotes;
 * - `packages/nested`, with Oxlint 1.80.0 and Oxfmt 0.70.0, `no-debugger` as a warning and double quotes;
 * - `packages/vite`, with `vite-plus` 1.5.0, whose `vite.config.ts` asks for a warning, double quotes and
 *   no semicolons.
 *
 * npm and Yarn's `node-modules` linker hoist `vite-plus` to the root's `node_modules`. pnpm and Bun link
 * each package into a store folder, `.pnpm` or `.bun`, and only `packages/vite` can resolve `vite-plus`.
 * The hoisted `vite-plus` causes two known defects, left for a later fix and not asserted here. D1: every
 * folder runs `vp`, which ignores `.oxlintrc.json` and `.oxfmtrc.json`. D2: `vp` runs from the workspace
 * root, so it never reads `packages/vite/vite.config.ts`.
 *
 * The fixtures' `node_modules` are too big to copy for each test, so the fixture's folder is added to the
 * project, and each `index.js` is put back after the test. `OXC_TEST_PACKAGE_MANAGER_FIXTURES` points at
 * another copy of the folder that holds the four fixtures. The WSL job installs one inside WSL.
 *
 * The first time `vp` runs for a workspace's `packageManager`, it downloads that manager before it answers,
 * which can take longer than these tests wait. After installing the fixtures, run
 * `.github/scripts/download-vite-plus-package-managers.mjs` once, as CI does.
 */
abstract class PackageManagerRealServerTest : OxcFixtureTestCase() {

  /** The fixture's folder name, which is the manager's name. */
  protected abstract val manager: String

  /** npm and Yarn hoist `vite-plus` to the root's `node_modules`, where every folder resolves it. */
  protected abstract val hoistsVitePlus: Boolean

  private val fixture: Path
    get() = (System.getenv(FIXTURES_VARIABLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_FIXTURES)
      .let { Path.of(it).toAbsolutePath().resolve(manager) }

  private lateinit var root: VirtualFile
  private val originals = mutableMapOf<VirtualFile, String>()

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    root = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixture)) {
      "No fixture at $fixture. Install it with its package manager first."
    }
    PsiTestUtil.addContentRoot(myFixture.module, root)
  }

  override fun tearDown() {
    try {
      for ((file, text) in originals) {
        WriteAction.runAndWait<Exception> { FileDocumentManager.getInstance().getDocument(file)!!.setText(text) }
      }
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testRootFolder() {
    val file = open("")

    val lint = launch(OxlintDefinition, file)
    val fmt = launch(OxfmtDefinition, file)
    assertEquals("Oxlint's server root", root, lint.root)
    assertEquals("Oxfmt's server root", root, fmt.root)
    assertEquals("Oxlint's schema", "oxlint@1.88.0", schemaPackage(root, ".oxlintrc.json"))
    assertEquals("Oxfmt's schema", "oxfmt@0.77.0", schemaPackage(root, ".oxfmtrc.json"))
    if (hoistsVitePlus) {
      // TODO D1: the root depends on oxlint and oxfmt, not vite-plus, but the hoisted vite-plus that
      //  packages/vite depends on wins: `vp lint --lsp` and `vp fmt --lsp` run from the root, and vp
      //  ignores `.oxlintrc.json` and `.oxfmtrc.json`. So no-debugger is a warning instead of an error,
      //  Format uses double quotes, and the overlay finds no config. Expected: the tools' own packages, an
      //  error, ROOT_FORMATTED and the root's `.oxfmtrc.json`.
      return
    }
    assertExecutable(lint, "node_modules/oxlint/bin/oxlint", "--lsp")
    assertExecutable(fmt, "node_modules/oxfmt/bin/oxfmt", "--lsp")
    waitForDebuggerSeverity(file, HighlightSeverity.ERROR)
    assertEquals("The running Oxlint server", lint, runningLaunch(OxlintDefinition, file))
    formatUntil(file, ROOT_FORMATTED)
    assertEquals("The running Oxfmt server", fmt, runningLaunch(OxfmtDefinition, file))
    assertJsonOverlay(file, root.findChild(".oxfmtrc.json")!!)
  }

  fun testNestedFolder() {
    val folder = root.findFileByRelativePath("packages/nested")!!
    val file = open("packages/nested")

    val lint = launch(OxlintDefinition, file)
    val fmt = launch(OxfmtDefinition, file)
    // Both configs agree with the servers' defaults here, so these hold whichever server runs.
    waitForDebuggerSeverity(file, HighlightSeverity.WARNING)
    assertEquals("The running Oxlint server", lint, runningLaunch(OxlintDefinition, file))
    formatUntil(file, NESTED_FORMATTED)
    assertEquals("The running Oxfmt server", fmt, runningLaunch(OxfmtDefinition, file))
    if (hoistsVitePlus) {
      // TODO D1: as for the root, the hoisted vite-plus wins over the package's own oxlint 1.80.0
      //  and oxfmt 0.70.0, so `vp` runs from the workspace root, the schemas are the root's 1.88.0 and
      //  0.77.0, and the overlay finds no config. Expected: the package's own tools, run from
      //  packages/nested, their schemas, and its `.oxfmtrc.json`.
      return
    }
    assertExecutable(lint, "packages/nested/node_modules/oxlint/bin/oxlint", "--lsp")
    assertExecutable(fmt, "packages/nested/node_modules/oxfmt/bin/oxfmt", "--lsp")
    assertEquals("Oxlint's server root", folder, lint.root)
    assertEquals("Oxfmt's server root", folder, fmt.root)
    assertEquals("Oxlint's schema", "oxlint@1.80.0", schemaPackage(folder, ".oxlintrc.json"))
    assertEquals("Oxfmt's schema", "oxfmt@0.70.0", schemaPackage(folder, ".oxfmtrc.json"))
    assertJsonOverlay(file, folder.findChild(".oxfmtrc.json")!!)
  }

  fun testVitePlusFolder() {
    val folder = root.findFileByRelativePath("packages/vite")!!
    val file = open("packages/vite")

    val lint = launch(OxlintDefinition, file)
    val fmt = launch(OxfmtDefinition, file)
    // Spec 4.3: the folder holding the `node_modules` that holds vite-plus.
    val expectedRoot = if (hoistsVitePlus) root else folder
    val vp = if (hoistsVitePlus) "node_modules/vite-plus/bin/vp" else "packages/vite/node_modules/vite-plus/bin/vp"
    assertExecutable(lint, vp, "lint", "--lsp")
    assertExecutable(fmt, vp, "fmt", "--lsp")
    assertEquals("Oxlint's server root", expectedRoot, lint.root)
    assertEquals("Oxfmt's server root", expectedRoot, fmt.root)
    // vp runs the oxlint that Node finds from its folder first, which is the root's, not its own 1.91.0.
    assertEquals("Oxlint's schema", "oxlint@1.88.0", schemaPackage(folder, ".oxlintrc.json"))
    // vite.config.ts and the defaults both make it a warning, so this holds whichever config is read.
    waitForDebuggerSeverity(file, HighlightSeverity.WARNING)
    assertEquals("The running Oxlint server", lint, runningLaunch(OxlintDefinition, file))
    if (hoistsVitePlus) {
      // TODO D2: vp runs from the workspace root, where there's no vite.config.ts, so it uses its
      //  defaults: Format keeps the semicolons and the overlay finds no config. Expected: VITE_FORMATTED
      //  and packages/vite/vite.config.ts read through Node.
      return
    }
    formatUntil(file, VITE_FORMATTED)
    assertEquals("The running Oxfmt server", fmt, runningLaunch(OxfmtDefinition, file))
    assertVitePlusOverlay(file, folder.findChild("vite.config.ts")!!)
  }

  /**
   * FR-LIFE-6: the root's servers start first, and the other folders still get their own, because a
   * server only answers for files that resolve to its launch.
   */
  fun testEveryFolderOpen() {
    val files = listOf("", "packages/nested", "packages/vite").map { folderPath ->
      open(folderPath).also { file -> ToolDefinition.all.forEach { runningLaunch(it, file) } }
    }

    for (definition in ToolDefinition.all) {
      val expected = files.map { launch(definition, it) }
      // TODO D1: with npm and Yarn every folder resolves to the root's hoisted vp, which is D1.
      assertEquals("${definition.displayName}'s launches", if (hoistsVitePlus) 1 else 3, expected.toSet().size)
      for (file in files) {
        val message = "The ${definition.displayName} server for ${file.path}"
        assertEquals(message, launch(definition, file), runningLaunch(definition, file))
      }
      val running = LspClientManager.getInstance(project).getClients(definition.providerClass)
        .map { (it.descriptor as ToolLspClientDescriptor).launch }
      assertEquals("Running ${definition.displayName} servers", expected.toSet(), running.toSet())
      assertEquals("Running ${definition.displayName} servers", running.toSet().size, running.size)
    }
    if (hoistsVitePlus) return

    myFixture.openFileInEditor(files[1])
    waitForDebuggerSeverity(files[1], HighlightSeverity.WARNING)
    formatUntil(files[2], VITE_FORMATTED)
  }

  /**
   * FR-LIFE-6: a settings change restarts every folder's servers, not only the root's. The platform's own
   * restart skips an open file under the root of a server it has already picked, so the nested and Vite+
   * files had no server until their editors were selected again, which this test doesn't do.
   */
  fun testSettingsChangeRestartsEveryFolderServer() {
    val files = listOf("", "packages/nested", "packages/vite").map(::open)
    val before = ToolDefinition.all.associateWith { definition ->
      files.forEach { runningLaunch(definition, it) }
      clients(definition).toSet()
    }

    OxlintSettings.getInstance(project).let { it.update(it.toData().copy(disableNestedConfig = true)) }
    OxfmtSettings.getInstance(project).let { it.update(it.toData().copy(disableNestedConfig = true)) }

    for (definition in ToolDefinition.all) {
      val earlier = before.getValue(definition)
      waitUntil("the earlier ${definition.displayName} servers have stopped") { clients(definition).none { it in earlier } }
      for (file in files) {
        val message = "The ${definition.displayName} server for ${file.path}"
        waitUntil("$message runs") {
          clients(definition).any { it.descriptor.isSupportedFile(file) && it.state == LspServerState.Running }
        }
        assertEquals(message, launch(definition, file), runningLaunch(definition, file))
      }
      val running = clients(definition).map { (it.descriptor as ToolLspClientDescriptor).launch }
      // TODO D1: with npm and Yarn every folder resolves to the root's hoisted vp, which is D1.
      assertEquals("Running ${definition.displayName} servers", if (hoistsVitePlus) 1 else 3, running.size)
      assertEquals("Running ${definition.displayName} servers", files.map { launch(definition, it) }.toSet(), running.toSet())
    }
  }

  private fun clients(definition: ToolDefinition): Collection<LspClient> =
    LspClientManager.getInstance(project).getClients(definition.providerClass)

  private fun open(folderPath: String): VirtualFile {
    val folder = if (folderPath.isEmpty()) root else root.findFileByRelativePath(folderPath)!!
    val file = folder.findChild("index.js")!!
    originals[file] = VfsUtilCore.loadText(file)
    myFixture.configureFromExistingVirtualFile(file)
    return file
  }

  private fun launch(definition: ToolDefinition, file: VirtualFile): ServerLaunch =
    checkNotNull(ToolPackage(project, definition).resolve(file)) { "Nothing to run for ${definition.displayName}" }

  /** The path below the fixture's folder, which is the same on every OS and in WSL. */
  private fun assertExecutable(launch: ServerLaunch, relativePath: String, vararg parameters: String) {
    val executable = PathUtil.toSystemIndependentName(launch.executable)
    assertTrue("$executable should end with $manager/$relativePath", executable.endsWith("/$manager/$relativePath"))
    assertEquals("Parameters of $executable", parameters.toList(), launch.parameters)
  }

  /** The launch of the one server that handles [file], once it has started. */
  private fun runningLaunch(definition: ToolDefinition, file: VirtualFile): ServerLaunch {
    fun clients() = LspClientManager.getInstance(project).getClients(definition.providerClass)
      .filter { it.descriptor.isSupportedFile(file) }
    waitUntil("a ${definition.displayName} server handles ${file.path}") { clients().isNotEmpty() }
    return (clients().single().descriptor as ToolLspClientDescriptor).launch
  }

  /** The `name@version` of the package whose schema the config gets. */
  private fun schemaPackage(folder: VirtualFile, configName: String): String? {
    val config = folder.findChild(configName)!!
    val schema = JsonSchemaService.Impl.get(project).getSchemaFilesForFile(config).singleOrNull() ?: return null
    val json = schema.parent.findChild("package.json")?.let(VfsUtilCore::loadText) ?: return schema.path
    fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    return "${field("name")}@${field("version")}"
  }

  private fun location(file: VirtualFile): OxfmtConfigLocation? =
    ReadAction.compute<OxfmtConfigLocation?, Exception> { locateOxfmtConfig(project, file) }

  private fun assertJsonOverlay(file: VirtualFile, config: VirtualFile) {
    val location = location(file)
    assertEquals("The overlay's config", config, location?.file)
    assertEquals("The overlay's format", OxfmtConfigFormat.JSON, location?.format)
  }

  /** Step 8: Node runs the real vite-plus's `resolveConfig` on `vite.config.ts` and gives its `fmt`. */
  private fun assertVitePlusOverlay(file: VirtualFile, config: VirtualFile) {
    val location = location(file)
    assertEquals("The overlay's config", config, location?.file)
    assertEquals("The overlay's format", OxfmtConfigFormat.VITE_PLUS, location?.format)
    val call = ApplicationManager.getApplication().executeOnPooledThread<OxfmtJsConfigResult> {
      runBlocking { OxfmtJsConfigLoader.getInstance(project).load(location!!) }
    }
    val result = PlatformTestUtil.waitForFuture(call, 90_000)
    assertTrue("Node should have read $config: $result", result is OxfmtJsConfigResult.Loaded)
    result as OxfmtJsConfigResult.Loaded
    assertEquals("The file with the options", config, result.file)
    assertFalse("singleQuote", result.config.singleQuote)
    assertFalse("semi", result.config.semi)
  }

  private fun debuggerSeverity(): HighlightSeverity? =
    myFixture.doHighlighting().firstOrNull { "`debugger` statement" in it.description.orEmpty() }?.severity

  /** No diagnostic also looks like the server not having answered yet, so this waits for a server first. */
  private fun waitForDebuggerSeverity(file: VirtualFile, expected: HighlightSeverity) {
    runningLaunch(OxlintDefinition, file)
    var last: HighlightSeverity? = null
    try {
      waitUntil("the debugger diagnostic has severity $expected", timeout = 60.seconds) {
        last = debuggerSeverity()
        last == expected
      }
    }
    catch (error: AssertionError) {
      throw AssertionError("Last severity: $last", error)
    }
  }

  /** Formats until the file is [expected], putting the document back each time so that it can be tried again. */
  private fun formatUntil(file: VirtualFile, expected: String) {
    val document = FileDocumentManager.getInstance().getDocument(file)!!
    val original = document.text
    var last = ""
    try {
      waitUntil("formatting gives ${expected.replace("\n", "\\n")}", timeout = 60.seconds) {
        val call = ApplicationManager.getApplication().executeOnPooledThread {
          runBlocking { OxcFixAll.getInstance(project).fixFile(OxfmtDefinition, file) }
        }
        PlatformTestUtil.waitForFuture(call, 60_000)
        last = document.text
        WriteAction.runAndWait<Exception> { document.setText(original) }
        last == expected
      }
    }
    catch (error: AssertionError) {
      throw AssertionError("Last result: ${last.replace("\n", "\\n")}", error)
    }
  }

  private companion object {
    const val FIXTURES_VARIABLE = "OXC_TEST_PACKAGE_MANAGER_FIXTURES"
    const val DEFAULT_FIXTURES = "src/test/testData/package-managers"
  }
}
