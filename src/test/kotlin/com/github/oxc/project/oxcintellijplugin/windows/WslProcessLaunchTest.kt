package com.github.oxc.project.oxcintellijplugin.windows

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.NodeProcessCommandBuilder
import com.github.oxc.project.oxcintellijplugin.OxcTargetRun
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting.onPooledThread
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.nio.file.Files
import java.nio.file.Path

/**
 * A server binary inside WSL, run with the distribution's Node.js as the project interpreter.
 * Runs on Windows when WSL tests are configured (see [WslTesting]); CI runs it in the Windows
 * test job.
 */
class WslProcessLaunchTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    private lateinit var distribution: WSLDistribution
    private lateinit var wslDir: Path

    private val projectDir: Path
        get() = Path.of(myFixture.tempDirPath)

    override fun shouldRunTest(): Boolean = WslTesting.enabled && super.shouldRunTest()

    override fun setUp() {
        WslTesting.disableIjent()
        super.setUp()
        distribution = WslTesting.distribution()
        wslDir = WslTesting.createTempDir(distribution)
        NodeJsInterpreterManager.getInstance(project).setInterpreterRef(WslTesting.nodeInterpreterRef(), testRootDisposable)
    }

    override fun tearDown() {
        try {
            // setUp may have failed before creating the directory.
            if (::wslDir.isInitialized) WslTesting.deleteTempDir(wslDir)
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testScriptRunsInsideTheDistributionWithConvertedPaths() {
        val index = projectDir.resolve("index.js")
        val run = build(ProcessCommandParameter.Value("--lsp"), ProcessCommandParameter.FilePath(index))

        val output = onPooledThread { run(run) }

        assertTrue(output, output.contains("platform=linux"))
        assertTrue(output, output.contains("args=[\"--lsp\",\"${wslPath(index)}\"]"))
        assertTrue(output, output.contains("cwd=${wslPath(projectDir)}"))
        // The server log level follows the plugin's log level and must reach the process inside WSL.
        val logger = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin")
        val level = if (logger.isTraceEnabled) "TRACE" else if (logger.isDebugEnabled) "DEBUG" else "INFO"
        assertTrue(output, output.contains("OXC_LOG=$level"))
        assertTrue(output, output.contains("RUST_LOG=$level"))
    }

    fun testPathsAreConvertedBetweenWindowsAndTheDistribution() {
        val run = build()
        val index = projectDir.resolve("index.js")
        val script = wslDir.resolve("server.js")

        val (toWsl, back, inside) = onPooledThread {
            Triple(run.toTargetPath(index.toString()), run.toLocalPath(wslPath(index)), run.toLocalPath(wslPath(script)))
        }

        assertEquals(wslPath(index), toWsl)
        assertEquals(index, Path.of(back))
        assertEquals(script, Path.of(inside))
    }

    private fun build(vararg parameters: ProcessCommandParameter): OxcTargetRun {
        val script = wslDir.resolve("server.js")
        Files.writeString(script, """
            #!/usr/bin/env node
            console.log("platform=" + process.platform);
            console.log("args=" + JSON.stringify(process.argv.slice(2)));
            console.log("cwd=" + process.cwd());
            console.log("OXC_LOG=" + process.env.OXC_LOG);
            console.log("RUST_LOG=" + process.env.RUST_LOG);
        """.trimIndent())
        val builder = OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, script.toString())
        assertInstanceOf(builder, NodeProcessCommandBuilder::class.java)
        return onPooledThread {
            builder.setWorkingDirectory(projectDir.toString()).addParameters(parameters.toList()).build()
        }
    }

    private fun wslPath(path: Path): String = onPooledThread { distribution.getWslPath(path) }
        ?: error("$path has no path inside ${distribution.msId}")

    private fun run(run: OxcTargetRun): String {
        val handler = run.startProcess()
        val adapter = CapturingProcessAdapter()
        handler.addProcessListener(adapter)
        handler.startNotify()
        assertTrue("process did not finish", handler.waitFor(60_000))
        return adapter.output.stdout + adapter.output.stderr
    }
}
