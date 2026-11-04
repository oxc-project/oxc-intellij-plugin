package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.testutil.NodeTooling
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.OSProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/** How the plugin decides how to launch a language server binary, and what it launches. */
class ProcessLaunchTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    private val projectDir: Path
        get() = Path.of(myFixture.tempDirPath)

    override fun setUp() {
        super.setUp()
        NodeJsInterpreterManager.getInstance(project)
            .setInterpreterRef(NodeJsInterpreterRef.create(NodeTooling.get().node.toString()), testRootDisposable)
    }

    fun testEmptyExecutableIsRejected() {
        assertThrows(ExecutionException::class.java) {
            OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.AUTOMATIC, "")
        }
    }

    fun testAutomaticModeAlwaysRunsThroughNode() {
        assertInstanceOf(OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.AUTOMATIC, nodeScript().toString()),
            NodeProcessCommandBuilder::class.java)
        assertInstanceOf(OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.AUTOMATIC, nativeScript().toString()),
            NodeProcessCommandBuilder::class.java)
    }

    fun testManualModeRunsNodeScriptsThroughNode() {
        assertInstanceOf(OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, nodeScript().toString()),
            NodeProcessCommandBuilder::class.java)
    }

    fun testManualModeRunsOtherExecutablesDirectly() {
        assertInstanceOf(OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, nativeScript().toString()),
            GeneralProcessCommandBuilder::class.java)
    }

    /**
     * QUESTIONABLE: only the exact shebang `#!/usr/bin/env node` is recognized as a Node.js script.
     * Scripts starting with e.g. `#!/usr/bin/env -S node --flag` or `#!/usr/local/bin/node` are
     * launched directly instead of through the configured interpreter, which bypasses the
     * project's Node.js version, WSL and remote interpreters.
     */
    fun testOtherNodeShebangsAreNotRecognized() {
        val script = write("alt-node.js", "#!/usr/bin/env -S node --no-warnings\nconsole.log('hi')\n")

        assertInstanceOf(OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, script.toString()),
            GeneralProcessCommandBuilder::class.java)
    }

    fun testNodeScriptReceivesParametersWorkingDirectoryAndLogLevel() {
        val run = OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.AUTOMATIC, nodeScript().toString())
            .setWorkingDirectory(projectDir.toString())
            .addParameters(listOf(ProcessCommandParameter.Value("lint"), ProcessCommandParameter.Value("--lsp")))
            .build()

        val output = run(run.startProcess())

        assertTrue(output, output.contains("args=[\"lint\",\"--lsp\"]"))
        assertTrue(output, output.contains("cwd=${projectDir.toRealPath()}"))
        // The server log level follows the plugin's log level (TRACE/DEBUG/INFO).
        val logger = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin")
        val level = if (logger.isTraceEnabled) "TRACE" else if (logger.isDebugEnabled) "DEBUG" else "INFO"
        assertTrue(output, output.contains("OXC_LOG=$level"))
        assertTrue(output, output.contains("RUST_LOG=$level"))
    }

    fun testNativeExecutableReceivesParametersAndWorkingDirectory() {
        if (SystemInfo.isWindows) return // The fixture is a POSIX shell script.

        val run = OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, nativeScript().toString())
            .setWorkingDirectory(projectDir.toString())
            .addParameters(listOf(ProcessCommandParameter.Value("--lsp")))
            .build()

        val output = run(run.startProcess())

        assertTrue(output, output.contains("args=--lsp"))
        assertTrue(output, output.contains("cwd=${projectDir.toRealPath()}"))
    }

    fun testLocalPathsAreUnchangedOutsideWsl() {
        val node = OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.AUTOMATIC, nodeScript().toString()).build()
        val general = OxcTargetRunBuilder(project).getBuilder(ConfigurationMode.MANUAL, nativeScript().toString()).build()

        val path = projectDir.resolve("index.js").toString()
        assertEquals(path, general.toTargetPath(path))
        assertEquals(path, general.toLocalPath(path))
        assertEquals(path, node.toLocalPath(node.toTargetPath(path)))
    }

    private fun nodeScript(): Path = write("server.js", """
        #!/usr/bin/env node
        console.log("args=" + JSON.stringify(process.argv.slice(2)));
        console.log("cwd=" + require("fs").realpathSync(process.cwd()));
        console.log("OXC_LOG=" + process.env.OXC_LOG);
        console.log("RUST_LOG=" + process.env.RUST_LOG);
    """.trimIndent())

    private fun nativeScript(): Path = write("server.sh", "#!/bin/sh\necho \"args=$*\"\necho \"cwd=$(pwd -P)\"\n")

    private fun write(name: String, text: String): Path {
        val path = projectDir.resolve(name)
        Files.writeString(path, text)
        if (!SystemInfo.isWindows) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        }
        return path
    }

    private fun run(handler: OSProcessHandler): String {
        val adapter = CapturingProcessAdapter()
        handler.addProcessListener(adapter)
        handler.startNotify()
        assertTrue("process did not finish", handler.waitFor(30_000))
        return adapter.output.stdout + adapter.output.stderr
    }
}
