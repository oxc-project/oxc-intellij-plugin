package com.github.oxc.project.oxcintellijplugin.windows

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.GeneralProcessCommandBuilder
import com.github.oxc.project.oxcintellijplugin.NodeProcessCommandBuilder
import com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder
import com.github.oxc.project.oxcintellijplugin.ProcessCommandParameter
import com.github.oxc.project.oxcintellijplugin.testutil.NodeTooling
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.nio.file.Files
import java.nio.file.Path

/**
 * How language server binaries are launched on Windows, outside WSL. Runs on Windows only; CI runs
 * it in the Windows test job.
 */
class WindowsProcessLaunchTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    private val projectDir: Path
        get() = Path.of(myFixture.tempDirPath)

    override fun shouldRunTest(): Boolean = SystemInfo.isWindows && super.shouldRunTest()

    override fun setUp() {
        super.setUp()
        NodeJsInterpreterManager.getInstance(project)
            .setInterpreterRef(NodeJsInterpreterRef.create(NodeTooling.get().node.toString()), testRootDisposable)
    }

    /**
     * A binary inside WSL (`\\wsl$\<distro>\...`) cannot be inspected for a shebang from Windows,
     * so it is always run through the Node.js interpreter, which knows how to run it inside WSL.
     */
    fun testWslPathsAlwaysRunThroughNode() {
        for (path in listOf("\\\\wsl$\\Ubuntu\\home\\me\\node_modules\\.bin\\oxlint",
            "\\\\wsl.localhost\\Ubuntu\\home\\me\\oxlint")) {
            assertInstanceOf(OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, path),
                NodeProcessCommandBuilder::class.java)
        }
    }

    /** `.cmd` and `.bat` files (such as npm's `node_modules\.bin` shims) cannot be started directly, so they run through `cmd.exe`. */
    fun testBatchFilesRunThroughCmdEvenInPathsWithSpaces() {
        for (name in listOf("server.cmd", "server.bat")) {
            val dir = Files.createDirectories(projectDir.resolve("dir with spaces"))
            val script = dir.resolve(name)
            Files.writeString(script, "@echo off\r\necho args=%*\r\necho cwd=%CD%\r\n")

            val builder = OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, script.toString())
            assertInstanceOf(builder, GeneralProcessCommandBuilder::class.java)
            val output = run(builder.setWorkingDirectory(projectDir.toString())
                .addParameters(listOf(ProcessCommandParameter.Value("--lsp")))
                .build().startProcess())

            assertTrue(output, output.contains("args=--lsp"))
            assertTrue(output, output.contains("cwd=$projectDir", ignoreCase = true))
        }
    }

    private fun run(handler: OSProcessHandler): String {
        val adapter = CapturingProcessAdapter()
        handler.addProcessListener(adapter)
        handler.startNotify()
        assertTrue("process did not finish", handler.waitFor(30_000))
        return adapter.output.stdout + adapter.output.stderr
    }
}
