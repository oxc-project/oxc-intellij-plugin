package com.github.oxc.project.oxcintellijplugin.testutil

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpManager
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions

/**
 * Base class for tests that run the real Oxlint/Oxfmt language servers.
 *
 * Fixtures come from `src/test/testData` and their dependencies are installed automatically with
 * [packageManager] (see [TestFixtures]); nothing has to be installed by hand before running tests.
 */
abstract class OxcLspTestCase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    protected open val packageManager: PackageManager = PackageManager.NPM

    protected val projectDir: Path
        get() = Path.of(myFixture.tempDirPath)

    override fun setUp() {
        super.setUp()
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
        // The fixture project has no Node.js interpreter configured; without one the plugin can
        // neither resolve packages nor start Node-based servers.
        NodeJsInterpreterManager.getInstance(project)
            .setInterpreterRef(NodeJsInterpreterRef.create(NodeTooling.get().node.toString()),
                testRootDisposable)
    }

    override fun tearDown() {
        try {
            stopServersAndWait()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Stops the servers and waits for the platform's LSP threads to finish. After a server dies
     * unexpectedly, the platform's listener thread is still cleaning up while the test ends, and
     * the fixture would report it as a leaked thread.
     *
     * The platform sends didOpen/didChange from a background read action that finishes on the EDT.
     * Let those finish first: one still queued when its server stops logs "Server is not in the
     * Running state", which fails the test.
     */
    private fun stopServersAndWait() {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val manager = LspServerManager.getInstance(project)
        manager.stopServers(OxlintLspServerSupportProvider::class.java)
        manager.stopServers(OxfmtLspServerSupportProvider::class.java)
        val projectPath = projectDir.toString()
        val projectName = project.name
        PlatformTestUtil.waitWithEventsDispatching({ "LSP threads did not finish" }, {
            Thread.getAllStackTraces().keys.none {
                it.isAlive && (it.name.contains(projectPath) || it.name.startsWith("LSP ") && it.name.contains(projectName))
            }
        }, 30)
    }

    /**
     * Copies [fixture] into the project (or into its [subdirectory]) and installs its dependencies
     * with [packageManager].
     */
    protected fun setUpFixture(fixture: String, subdirectory: String = "") {
        TestFixtures.setUp(fixture, packageManager, projectDir.resolve(subdirectory))
        refreshProjectDir()
        if (packageManager == PackageManager.YARN_PNP) {
            registerYarnPnp()
        }
    }

    /**
     * In the IDE, `.pnp.cjs` files are discovered while the project is scanned on open. The fixture
     * is copied in after the test project is opened, so register it the same way and wait for
     * IntelliJ to load the PnP dependency tree.
     */
    private fun registerYarnPnp() {
        val pnpFile = projectFile(".pnp.cjs")
        val packageJson = projectFile("package.json")
        val manager = YarnPnpManager.getInstance(project)
        manager.addPnpJsFile(pnpFile)
        PlatformTestUtil.waitWithEventsDispatching("Yarn PnP dependencies were not loaded", {
            manager.isUnderPnp(packageJson)
        }, 60)
    }

    /**
     * Points Oxlint and/or Oxfmt (MANUAL mode) at `testData/fake-server/fake-lsp.js`, a scriptable
     * language server that can crash, hang or fail; see that script for the [behavior] format. The
     * project gets an `index.js` to open.
     */
    protected fun setUpFakeServer(behavior: String = "{}", oxlint: Boolean = true, oxfmt: Boolean = true) {
        TestFixtures.copySources("fake-server", fakeServerDir)
        setFakeServerBehavior(behavior)
        Files.writeString(projectDir.resolve("index.js"), "let a = {b:1}\n")
        refreshProjectDir()
        val script = fakeServerDir.resolve("fake-lsp.js").toString()
        if (oxlint) {
            OxlintSettings.getInstance(project).apply {
                configurationMode = ConfigurationMode.MANUAL
                binaryPath = script
            }
        }
        if (oxfmt) {
            OxfmtSettings.getInstance(project).apply {
                configurationMode = ConfigurationMode.MANUAL
                binaryPath = script
            }
        }
    }

    /** Where [setUpFakeServer] puts the fake server, its behavior and its logs. */
    protected open val fakeServerDir: Path
        get() = projectDir.resolve("fake-server")

    /** Changes how the fake server behaves from its next start on. */
    protected fun setFakeServerBehavior(behavior: String) {
        Files.writeString(fakeServerDir.resolve("fake-lsp.json"), behavior)
    }

    /** The methods of all messages the fake server has received, in order. */
    protected fun fakeServerLog(): List<String> = readLines(fakeServerDir.resolve("fake-lsp.log"))

    /** The file URIs the fake server has been given, as `<where> <uri>`, in order. */
    protected fun fakeServerUris(): List<String> = readLines(fakeServerDir.resolve("fake-lsp-uris.log"))

    private fun readLines(file: Path): List<String> =
        if (Files.exists(file)) Files.readAllLines(file) else emptyList()

    protected fun refreshProjectDir(): VirtualFile {
        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir)!!
        VfsUtil.markDirtyAndRefresh(false, true, true, root)
        return root
    }

    protected fun projectFile(relativePath: String): VirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectDir.resolve(relativePath))
        ?: error("Missing $relativePath in the test project")

    protected fun openFile(relativePath: String): VirtualFile {
        val file = projectFile(relativePath)
        myFixture.configureFromExistingVirtualFile(file)
        return file
    }

    protected fun waitForOxlintServer(): LspServer = waitForServer(OxlintLspServerSupportProvider::class.java)

    protected fun waitForOxfmtServer(): LspServer = waitForServer(OxfmtLspServerSupportProvider::class.java)

    protected fun waitForServer(provider: Class<out LspServerSupportProvider>,
        timeoutSeconds: Int = 60): LspServer {
        val manager = LspServerManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching(
            { "No running server for ${provider.simpleName}; servers: ${manager.getServersForProvider(provider).map { it.state }}" },
            { manager.getServersForProvider(provider).any { it.state == LspServerState.Running } },
            timeoutSeconds)
        return manager.getServersForProvider(provider).first { it.state == LspServerState.Running }
    }

    protected fun servers(provider: Class<out LspServerSupportProvider>): Collection<LspServer> =
        LspServerManager.getInstance(project).getServersForProvider(provider)

    /** Oxlint highlights in the current editor; their descriptions start with `oxc:`. */
    protected fun oxlintHighlights(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.description?.startsWith("oxc:") == true }

    /**
     * Re-runs highlighting until [condition] holds. Oxlint uses pull diagnostics, which arrive
     * asynchronously after the daemon asks for them, so a single pass is not enough.
     */
    protected fun waitForOxlintHighlights(timeoutSeconds: Int = 60,
        condition: (List<HighlightInfo>) -> Boolean): List<HighlightInfo> {
        var last: List<HighlightInfo> = emptyList()
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            last = oxlintHighlights()
            if (condition(last)) {
                return last
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(250)
        }
        fail("Timed out waiting for Oxlint highlights. Last seen:\n" + last.joinToString("\n") { it.description })
        return last
    }

    protected fun waitForOxlintRules(vararg rules: String): List<HighlightInfo> =
        waitForOxlintHighlights { highlights -> rules.all { rule -> highlights.any { it.description.contains(rule) } } }

    /** Runs [block] on a background thread while dispatching EDT events, like the IDE would. */
    protected fun <T> runInBackground(timeoutSeconds: Int = 60, block: suspend () -> T): T {
        val future = ApplicationManager.getApplication().executeOnPooledThread(Callable {
            runBlocking { block() }
        })
        PlatformTestUtil.waitWithEventsDispatching("Background task timed out", { future.isDone },
            timeoutSeconds)
        return future.get()
    }

    /** Collects notifications shown in this project from now on. */
    protected fun collectNotifications(): List<Notification> {
        val notifications = java.util.concurrent.CopyOnWriteArrayList<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications.add(notification)
            }
        })
        return notifications
    }

    /** Waits until no server of [provider] is running any more. */
    protected fun waitForNoRunningServer(provider: Class<out LspServerSupportProvider>, timeoutSeconds: Int = 30) {
        PlatformTestUtil.waitWithEventsDispatching({ "Server for ${provider.simpleName} is still running" }, {
            servers(provider).none { it.state == LspServerState.Running || it.state == LspServerState.Initializing }
        }, timeoutSeconds)
    }

    /** Writes [text] to [relativePath] on disk, as an external tool or `git checkout` would. */
    protected fun writeProjectFile(relativePath: String, text: String) {
        val file = projectFile(relativePath)
        WriteAction.runAndWait<Throwable> { VfsUtil.saveText(file, text) }
    }

    private fun oxfmtServerFor(file: VirtualFile): LspServer? =
        servers(OxfmtLspServerSupportProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running && it.descriptor.isSupportedFile(file) }

    /** Asks the running Oxfmt server to format [file] and returns the resulting text. */
    protected fun formatWithServer(file: VirtualFile, document: Document = myFixture.editor.document): String {
        PlatformTestUtil.waitWithEventsDispatching("No running Oxfmt server for ${file.path}", {
            oxfmtServerFor(file) != null
        }, 60)
        val server = oxfmtServerFor(file)!!
        val edits = runInBackground {
            server.sendRequestSync(30_000) {
                it.textDocumentService.formatting(
                    DocumentFormattingParams(server.getDocumentIdentifier(file), FormattingOptions(2, true)))
            }
        }.orEmpty() // The server responds with null when the file is already formatted.

        var formatted = document.text
        for (edit in edits.sortedByDescending {
            document.getLineStartOffset(it.range.start.line) + it.range.start.character
        }) {
            val start = document.getLineStartOffset(edit.range.start.line) + edit.range.start.character
            val end = document.getLineStartOffset(edit.range.end.line) + edit.range.end.character
            formatted = formatted.replaceRange(start, end, edit.newText)
        }
        return formatted
    }
}
