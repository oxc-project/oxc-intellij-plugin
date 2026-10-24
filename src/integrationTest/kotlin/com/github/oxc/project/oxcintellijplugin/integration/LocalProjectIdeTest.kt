package com.github.oxc.project.oxcintellijplugin.integration

import java.nio.file.Files
import java.nio.file.Path

/** A project on the local file system, run with the local Node.js. */
class LocalProjectIdeTest : OxcIdeTest() {

    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    override fun createProjectDir(): Path = Files.createTempDirectory("oxc-ide-test")

    override fun installDependencies(projectDir: Path) {
        run(if (isWindows) "npm.cmd" else "npm", "install", "--no-audit", "--no-fund", directory = projectDir)
    }

    /** `OXC_TEST_NODE`, or the first `node` on `PATH`. */
    override val nodeInterpreter: String
        get() = System.getenv("OXC_TEST_NODE")?.takeIf { it.isNotBlank() }
            ?: System.getenv("PATH").split(java.io.File.pathSeparator)
                .map { Path.of(it, if (isWindows) "node.exe" else "node") }
                .firstOrNull { Files.isExecutable(it) }?.toString()
            ?: error("Node.js not found; put it on PATH or set OXC_TEST_NODE")
}
