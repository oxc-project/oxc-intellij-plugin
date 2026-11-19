package com.github.oxc.project.oxcintellijplugin.packagemanagers

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.OxfmtDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigFormat
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtConfigLocation
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigLoader
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.OxfmtJsConfigResult
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.locateOxfmtConfig
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.backend.tool.ServerLaunch
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolPackage
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpManager
import com.intellij.javascript.nodejs.npm.NpmManager
import com.intellij.javascript.nodejs.npm.NpmNodePackage
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.PathUtil
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking

private const val ROOT_FORMATTED = "debugger;\nconst a = 'b';\n"
private const val NESTED_FORMATTED = "debugger;\nconst a = \"b\";\n"
private const val VITE_FORMATTED = "debugger\nconst a = \"b\"\n"

/** Where Yarn puts a package it unplugs from its zip, such as `oxlint-virtual-65c4d58e5e`. */
private const val UNPLUGGED = "/yarn-pnp/\\.yarn/unplugged/[^/]+"

/** `vite-plus` stays in its zip, which Node reads through the PnP runtime. */
private const val ZIPPED_VP = "/yarn-pnp/\\.yarn/__virtual__/vite-plus-virtual-[^/]+/\\d+/cache/" +
  "vite-plus-npm-1\\.5\\.0-[^/]+\\.zip/node_modules/vite-plus/bin/vp"

/**
 * The workspace of [PackageManagerRealServerTest], installed by Yarn 4 with the `pnp` linker, with
 * real servers. There's no `node_modules`: Yarn unplugs `oxlint` and `oxfmt` into
 * `.yarn/unplugged`, and `vite-plus` stays in its zip in the fixture's own `.yarn/cache`. A
 * workspace only resolves the packages it declares, so `vp` in `packages/vite` runs the `oxlint`
 * and `oxfmt` that `vite-plus` depends on.
 *
 * The platform runs Node with `yarn node`, both to load the PnP dependency tree and to start the
 * servers, so the project's package manager is set to the fixture's own Yarn release. A `yarn` on
 * the PATH isn't there in WSL or on a machine without a global Yarn.
 *
 * The platform loads the tree in the background after it finds `.pnp.cjs`, and a file opened before
 * that gets no server, because nothing resolves it again. Each test waits for the tree first.
 *
 * The platform finds no PnP package for a project folder on another Windows drive than the fixture,
 * so on Windows CI sets `OXC_TEST_TMPDIR` to a temp folder on the checkout's drive.
 * `OXC_TEST_PACKAGE_MANAGER_FIXTURES` points at another copy of the fixtures, as for
 * [PackageManagerRealServerTest].
 */
class YarnPnpRealServerTest : OxcFixtureTestCase() {

  private val fixture: Path
    get() = (System.getenv(FIXTURES_VARIABLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_FIXTURES)
      .let { Path.of(it).toAbsolutePath().resolve(MANAGER) }

  private lateinit var root: VirtualFile
  private val originals = mutableMapOf<VirtualFile, String>()

  /**
   * With a WSL interpreter the platform never loads the PnP tree, because starting `yarn node` in
   * WSL from its pooled thread throws "There is no ProgressIndicator or Job in this thread", so
   * only the test that doesn't need the tree runs.
   *
   * On Windows, `vp lint --lsp` exits before its server starts: vite-plus 1.5.0 only takes
   * `tsgolint.exe` or `tsgolint.cmd` from a `node_modules/.bin` folder, which Yarn PnP doesn't
   * create.
   */
  override fun shouldRunTest(): Boolean = when {
    WslTesting.enabled -> name == "testYarnIsTheFixturesRelease"
    SystemInfo.isWindows -> name != "testVitePlusFolderRunsVpFromItsZip"
    else -> true
  } && super.shouldRunTest()

  /**
   * Checks the fixture on disk: until it's a content root, the test framework only lets the VFS
   * load files in the build and temp folders, which on Windows don't hold the test data.
   */
  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    val install = "node .yarn/releases/$YARN_RELEASE install --immutable"
    check(Files.isDirectory(fixture)) { "No fixture at $fixture. Install it with `$install`." }
    check(Files.isRegularFile(fixture.resolve(PNP_FILE))) { "No $PNP_FILE. Run `$install`." }
    val yarn = fixture.resolve(".yarn/releases/$YARN_RELEASE")
    check(Files.isRegularFile(yarn)) { "No $yarn" }
    // A WSL interpreter only takes a Linux path, or one such as `\\wsl.localhost\…`.
    val yarnPath = yarn.toString().let { if (WslTesting.enabled) WslTesting.toWslPath(it) else it }
    NpmManager.getInstance(project).packageRef = NodePackageRef.create(NpmNodePackage(yarnPath))
    root = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(fixture)) {
      "No fixture at $fixture"
    }
    PsiTestUtil.addContentRoot(myFixture.module, root)
  }

  override fun tearDown() {
    try {
      val documents = FileDocumentManager.getInstance()
      for ((file, text) in originals) {
        WriteAction.runAndWait<Exception> { documents.getDocument(file)!!.setText(text) }
      }
      documents.saveAllDocuments()
    } catch (e: Throwable) {
      addSuppressedException(e)
    } finally {
      super.tearDown()
    }
  }

  fun testYarnIsTheFixturesRelease() {
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
    val yarn = NpmManager.getInstance(project).getPackage(interpreter)
    assertNotNull("Yarn for `yarn node`", yarn)
    val path = PathUtil.toSystemIndependentName(yarn!!.systemIndependentPath)
    val release = "/$MANAGER/.yarn/releases/$YARN_RELEASE"
    assertTrue("$yarn should be the fixture's release", path.endsWith(release))
  }

  fun testRootFolderRunsTheRootsUnpluggedTools() {
    val file = open("")

    val lint = launch(OxlintDefinition, file)
    val fmt = launch(OxfmtDefinition, file)
    assertExecutable(lint, "$UNPLUGGED/node_modules/oxlint/bin/oxlint", "--lsp")
    assertExecutable(fmt, "$UNPLUGGED/node_modules/oxfmt/bin/oxfmt", "--lsp")
    assertPackageVersion(lint, "1.88.0")
    assertPackageVersion(fmt, "0.77.0")
    assertEquals("Oxlint's server root", root, lint.root)
    assertEquals("Oxfmt's server root", root, fmt.root)
    assertEquals("Oxlint's schema", "oxlint@1.88.0", schemaPackage(root, ".oxlintrc.json"))
    assertEquals("Oxfmt's schema", "oxfmt@0.77.0", schemaPackage(root, ".oxfmtrc.json"))
    waitForDebuggerSeverity(file, HighlightSeverity.ERROR)
    assertEquals("The running Oxlint server", lint, runningLaunch(OxlintDefinition, file))
    formatUntil(file, ROOT_FORMATTED)
    assertEquals("The running Oxfmt server", fmt, runningLaunch(OxfmtDefinition, file))
    assertJsonOverlay(file, root.findChild(".oxfmtrc.json")!!)
  }

  fun testNestedFolderRunsItsOwnUnpluggedTools() {
    val folder = root.findFileByRelativePath("packages/nested")!!
    val file = open("packages/nested")

    val lint = launch(OxlintDefinition, file)
    val fmt = launch(OxfmtDefinition, file)
    assertExecutable(lint, "$UNPLUGGED/node_modules/oxlint/bin/oxlint", "--lsp")
    assertExecutable(fmt, "$UNPLUGGED/node_modules/oxfmt/bin/oxfmt", "--lsp")
    assertPackageVersion(lint, "1.80.0")
    assertPackageVersion(fmt, "0.70.0")
    assertEquals("Oxlint's server root", folder, lint.root)
    assertEquals("Oxfmt's server root", folder, fmt.root)
    assertEquals("Oxlint's schema", "oxlint@1.80.0", schemaPackage(folder, ".oxlintrc.json"))
    assertEquals("Oxfmt's schema", "oxfmt@0.70.0", schemaPackage(folder, ".oxfmtrc.json"))
    waitForDebuggerSeverity(file, HighlightSeverity.WARNING)
    assertEquals("The running Oxlint server", lint, runningLaunch(OxlintDefinition, file))
    formatUntil(file, NESTED_FORMATTED)
    assertEquals("The running Oxfmt server", fmt, runningLaunch(OxfmtDefinition, file))
    assertJsonOverlay(file, folder.findChild(".oxfmtrc.json")!!)
  }

  fun testVitePlusFolderRunsVpFromItsZip() {
    val folder = root.findFileByRelativePath("packages/vite")!!
    val file = open("packages/vite")

    val lint = launch(OxlintDefinition, file)
    val fmt = launch(OxfmtDefinition, file)
    assertExecutable(lint, ZIPPED_VP, "lint", "--lsp")
    assertExecutable(fmt, ZIPPED_VP, "fmt", "--lsp")
    assertEquals("Oxlint's server root", folder, lint.root)
    assertEquals("Oxfmt's server root", folder, fmt.root)
    // The workspace doesn't declare oxlint and can't fall back to the root's, so vp runs its own.
    assertEquals("Oxlint's schema", "oxlint@1.91.0", schemaPackage(folder, ".oxlintrc.json"))
    waitForDebuggerSeverity(file, HighlightSeverity.WARNING)
    assertEquals("The running Oxlint server", lint, runningLaunch(OxlintDefinition, file))
    formatUntil(file, VITE_FORMATTED)
    assertEquals("The running Oxfmt server", fmt, runningLaunch(OxfmtDefinition, file))
    assertVitePlusOverlay(file, folder.findChild("vite.config.ts")!!)
  }

  /** A zipped package is a folder in the archive file system, so a schema in one is found too. */
  fun testZippedPackageIsAnArchiveFolder() {
    val packageJson = root.findFileByRelativePath("packages/vite/package.json")!!
    waitForPnpTree(packageJson)

    val vitePlus = YarnPnpManager.getInstance(project)
      .findInstalledPackageDir(packageJson, "vite-plus")
    assertNotNull("vite-plus's folder", vitePlus)
    assertTrue("$vitePlus should be in a zip", vitePlus!!.fileSystem is JarFileSystem)
    assertNotNull("vite-plus's package.json in the zip", vitePlus.findChild("package.json"))
  }

  /** Waits for the PnP tree, then opens the folder's `index.js`. */
  private fun open(folderPath: String): VirtualFile {
    val folder = if (folderPath.isEmpty()) root else root.findFileByRelativePath(folderPath)!!
    val file = folder.findChild("index.js")!!
    waitForPnp("Oxlint and Oxfmt resolve for ${file.path}") {
      ToolDefinition.all.all { ToolPackage(project, it).resolve(file) != null }
    }
    originals[file] = VfsUtilCore.loadText(file)
    myFixture.configureFromExistingVirtualFile(file)
    return file
  }

  private fun waitForPnpTree(packageJson: VirtualFile) {
    waitForPnp("vite-plus is in the tree") {
      YarnPnpManager.getInstance(project).findDependency(packageJson, "vite-plus") != null
    }
  }

  /**
   * Waits until [condition] holds once the platform has loaded the PnP tree. The platform loads it
   * once and only logs a failure, so a failed load throws its error here at once.
   */
  private fun waitForPnp(description: String, condition: () -> Boolean) {
    val pnp = root.findChild(PNP_FILE)!!
    val manager = YarnPnpManager.getInstance(project)
    try {
      waitUntil("$description once the PnP tree has loaded", timeout = 60.seconds) {
        manager.findPnpFile(pnp)?.checkFileRefreshedSuccessfully()
        condition()
      }
    } catch (error: AssertionError) {
      val pnpFile = manager.findPnpFile(pnp)
      val state = when {
        pnpFile == null -> "never took up $PNP_FILE"
        pnpFile.workspaceList == null -> "found $PNP_FILE but has no tree"
        else -> "loaded the tree"
      }
      throw AssertionError("${error.message}. The platform $state.${pnpLoaderStacks()}", error)
    }
  }

  /** Where the platform's PnP loading threads are, for a load that neither finished nor failed. */
  private fun pnpLoaderStacks(): String =
    Thread.getAllStackTraces()
      .filterValues { stack -> stack.any { ".yarn.pnp." in it.className } }
      .entries.joinToString("") { (thread, stack) ->
        "\n\n${thread.name} (${thread.state}):" + stack.take(60).joinToString("") { "\n  at $it" }
      }

  private fun launch(definition: ToolDefinition, file: VirtualFile): ServerLaunch =
    checkNotNull(ToolPackage(project, definition).resolve(file)) {
      "Nothing to run for ${definition.displayName}"
    }

  /** [pattern] is a regular expression for the end of the path, the same on every OS and in WSL. */
  private fun assertExecutable(launch: ServerLaunch, pattern: String, vararg parameters: String) {
    val executable = PathUtil.toSystemIndependentName(launch.executable)
    val matches = Regex("$pattern$").containsMatchIn(executable)
    assertTrue("$executable should end with $pattern", matches)
    assertEquals("Parameters of $executable", parameters.toList(), launch.parameters)
  }

  /** The version in the `package.json` of an unplugged package that [launch] runs. */
  private fun assertPackageVersion(launch: ServerLaunch, version: String) {
    val bin = LocalFileSystem.getInstance()
      .findFileByPath(PathUtil.toSystemIndependentName(launch.executable))
    val json = bin?.parent?.parent?.findChild("package.json")?.let(VfsUtilCore::loadText)
    assertEquals("The version of ${launch.executable}", version, json?.let { field(it, "version") })
  }

  /** The launch of the one server that handles [file], once it has started. */
  private fun runningLaunch(definition: ToolDefinition, file: VirtualFile): ServerLaunch {
    fun handling() = LspClientManager.getInstance(project).getClients(definition.providerClass)
      .filter { it.descriptor.isSupportedFile(file) }
    waitUntil("a ${definition.displayName} server handles ${file.path}") { handling().isNotEmpty() }
    return (handling().single().descriptor as ToolLspClientDescriptor).launch
  }

  /** The `name@version` of the package whose schema the config gets. */
  private fun schemaPackage(folder: VirtualFile, configName: String): String? {
    val config = folder.findChild(configName)!!
    val schema = JsonSchemaService.Impl.get(project).getSchemaFilesForFile(config).singleOrNull()
                 ?: return null
    val json = schema.parent.findChild("package.json")?.let(VfsUtilCore::loadText)
               ?: return schema.path
    return "${field(json, "name")}@${field(json, "version")}"
  }

  private fun field(json: String, name: String): String? =
    Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

  private fun location(file: VirtualFile): OxfmtConfigLocation? =
    runReadAction { locateOxfmtConfig(project, file) }

  private fun assertJsonOverlay(file: VirtualFile, config: VirtualFile) {
    val location = location(file)
    assertEquals("The overlay's config", config, location?.file)
    assertEquals("The overlay's format", OxfmtConfigFormat.JSON, location?.format)
  }

  /** Node runs the real vite-plus's `resolveConfig` on `vite.config.ts` and gives its `fmt`. */
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
    myFixture.doHighlighting()
      .firstOrNull { "`debugger` statement" in it.description.orEmpty() }
      ?.severity

  /** No diagnostic also looks like a server that hasn't answered, so this waits for one first. */
  private fun waitForDebuggerSeverity(file: VirtualFile, expected: HighlightSeverity) {
    runningLaunch(OxlintDefinition, file)
    var last: HighlightSeverity? = null
    try {
      waitUntil("the debugger diagnostic has severity $expected", timeout = 60.seconds) {
        last = debuggerSeverity()
        last == expected
      }
    } catch (error: AssertionError) {
      throw AssertionError("Last severity: $last", error)
    }
  }

  /** Formats until the file is [expected], putting the document back after each try. */
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
    } catch (error: AssertionError) {
      throw AssertionError("Last result: ${last.replace("\n", "\\n")}", error)
    }
  }

  private companion object {

    const val MANAGER = "yarn-pnp"
    const val PNP_FILE = ".pnp.cjs"
    const val YARN_RELEASE = "yarn-4.18.1.cjs"
    const val FIXTURES_VARIABLE = "OXC_TEST_PACKAGE_MANAGER_FIXTURES"
    const val DEFAULT_FIXTURES = "src/test/testData/package-managers"
  }
}
