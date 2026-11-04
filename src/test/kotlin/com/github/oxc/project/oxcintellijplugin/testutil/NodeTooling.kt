package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

/**
 * Locates Node.js and provisions pinned versions of pnpm, Yarn and Bun, so the tests do not depend
 * on whatever package managers (if any) happen to be installed on the machine.
 *
 * Node.js itself is the only prerequisite. It is looked up in `OXC_TEST_NODE` (path to the node
 * executable) and then on `PATH`. npm is the copy bundled with that Node.js installation; pnpm,
 * Yarn and Bun are installed with that npm into `build/oxc-test-tools` the first time they are
 * needed and reused afterwards.
 */
class NodeTooling private constructor(val node: Path, val npmCli: Path, private val toolsDir: Path) {

    val pnpm: Path
        get() = binShim("pnpm")

    val bun: Path
        get() = binShim("bun")

    val yarnJs: Path
        get() = toolsDir.resolve("node_modules/@yarnpkg/cli-dist/bin/yarn.js")

    /** Identifies the provisioned tool versions; part of the fixture cache key. */
    val versionKey: String = "node=${nodeVersion(node)};$TOOL_PACKAGES"

    /** Environment for running Node.js tools: the selected Node.js first on `PATH`, no prompts. */
    fun environment(): Map<String, String> {
        val pathKey = System.getenv().keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val path = listOfNotNull(node.parent.toString(), System.getenv(pathKey))
            .joinToString(java.io.File.pathSeparator)
        return mapOf(
            pathKey to path,
            "CI" to "true",
            "npm_config_update_notifier" to "false",
            "npm_config_fund" to "false",
            "npm_config_audit" to "false",
            "COREPACK_ENABLE_STRICT" to "0",
            "COREPACK_ENABLE_DOWNLOAD_PROMPT" to "0",
            "YARN_ENABLE_TELEMETRY" to "0",
            "YARN_ENABLE_IMMUTABLE_INSTALLS" to "false",
            "BUN_INSTALL_CACHE_DIR" to toolsDir.resolve("bun-cache").toString(),
            "DO_NOT_TRACK" to "1",
        )
    }

    private fun binShim(name: String): Path {
        val bin = toolsDir.resolve("node_modules/.bin")
        return if (SystemInfo.isWindows) bin.resolve("$name.cmd") else bin.resolve(name)
    }

    companion object {
        /**
         * Pinned package manager versions. Bump these deliberately; the fixture cache is keyed on
         * them, so a bump reinstalls every fixture.
         */
        private const val TOOL_PACKAGES = "pnpm@12.14.0 @yarnpkg/cli-dist@4.18.1 bun@1.4.3"

        private val toolsRoot: Path = Path.of("build", "oxc-test-tools").absolute()

        @Volatile
        private var instance: NodeTooling? = null

        fun get(): NodeTooling {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val node = findNode()
                val npmCli = findNpmCli(node)
                val tools = NodeTooling(node, npmCli, toolsRoot)
                tools.provision()
                instance = tools
                return tools
            }
        }

        private fun findNode(): Path {
            System.getenv("OXC_TEST_NODE")?.takeIf { it.isNotBlank() }?.let {
                val path = Path.of(it)
                check(path.isRegularFile()) { "OXC_TEST_NODE points to a missing file: $it" }
                return path
            }
            val names = if (SystemInfo.isWindows) listOf("node.exe") else listOf("node")
            val pathValue = System.getenv().entries
                .firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value.orEmpty()
            for (dir in pathValue.split(java.io.File.pathSeparator).filter { it.isNotBlank() }) {
                for (name in names) {
                    val candidate = Path.of(dir, name)
                    if (candidate.isRegularFile() && candidate.isExecutable() && runsNode(candidate)) {
                        return candidate.toRealPath()
                    }
                }
            }
            throw IllegalStateException(
                "Node.js is required to run the Oxc language server tests. Install Node.js and put it " +
                "on PATH, or set OXC_TEST_NODE to the node executable.")
        }

        private fun runsNode(candidate: Path): Boolean = runCatching { nodeVersion(candidate) }
            .getOrNull()?.startsWith("v") == true

        private fun nodeVersion(node: Path): String {
            val process = ProcessBuilder(node.toString(), "--version").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor(30, TimeUnit.SECONDS)
            return output
        }

        private fun findNpmCli(node: Path): Path {
            val nodeDir = node.parent
            val candidates = listOf(
                nodeDir.resolve("node_modules/npm/bin/npm-cli.js"),          // Windows layout
                nodeDir.resolveSibling("lib/node_modules/npm/bin/npm-cli.js"), // Unix layout
            )
            return candidates.firstOrNull { it.exists() }
                   ?: throw IllegalStateException("Unable to find npm next to $node (looked in $candidates)")
        }
    }

    private fun provision() {
        val marker = toolsDir.resolve(".provisioned")
        if (marker.exists() && Files.readString(marker) == versionKey) {
            return
        }
        Files.createDirectories(toolsDir)
        Files.writeString(toolsDir.resolve("package.json"), """{ "private": true }""")
        ProcessRunner.run(
            listOf(node.toString(), npmCli.toString(), "install", "--no-save", "--no-audit", "--no-fund",
                "--no-package-lock") + TOOL_PACKAGES.split(" "),
            toolsDir, environment(), "provision package managers")
        // Fail early with a clear message rather than in the middle of a fixture install.
        ProcessRunner.run(listOf(pnpm.toString(), "--version"), toolsDir, environment(), "pnpm --version")
        ProcessRunner.run(listOf(node.toString(), yarnJs.toString(), "--version"), toolsDir, environment(),
            "yarn --version")
        ProcessRunner.run(listOf(bun.toString(), "--version"), toolsDir, environment(), "bun --version")
        Files.writeString(marker, versionKey)
    }
}

internal object ProcessRunner {
    fun run(command: List<String>, workingDir: Path, environment: Map<String, String>, description: String,
        timeoutMinutes: Long = 10): String {
        val builder = ProcessBuilder(command).directory(workingDir.toFile()).redirectErrorStream(true)
        builder.environment().putAll(environment)
        val process = builder.start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
        }.apply { isDaemon = true; start() }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw IllegalStateException("Timed out: $description\n$command\n$output")
        }
        reader.join(5_000)
        check(process.exitValue() == 0) {
            "Failed (exit ${process.exitValue()}): $description\n${command.joinToString(" ")}\nin $workingDir\n$output"
        }
        return output.toString()
    }
}
