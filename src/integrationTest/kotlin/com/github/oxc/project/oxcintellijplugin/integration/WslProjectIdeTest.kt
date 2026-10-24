package com.github.oxc.project.oxcintellijplugin.integration

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * A project stored inside WSL and opened from Windows, with `oxlint` and `oxfmt` installed by npm
 * inside WSL and WSL's Node.js as the project interpreter: the usual setup for WSL users. The IDE
 * runs with its defaults, so it reaches WSL through IJent as it does for users. npm installs the
 * Linux builds of Oxlint and Oxfmt, so the test only passes if the servers really run inside WSL.
 *
 * Runs on Windows when `OXC_TEST_WSL_DISTRO` names an installed distribution with Node.js and npm
 * (Node.js at `OXC_TEST_WSL_NODE`, default `/usr/bin/node`).
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named = "OXC_TEST_WSL_DISTRO", matches = ".+")
class WslProjectIdeTest : OxcIdeTest() {

    private val distribution = System.getenv("OXC_TEST_WSL_DISTRO").orEmpty()

    private val linuxProjectDir = "/tmp/oxc-ide-test-${UUID.randomUUID()}"

    override fun createProjectDir(): Path {
        run("wsl.exe", "-d", distribution, "--", "mkdir", "-p", linuxProjectDir)
        return Path.of("\\\\wsl.localhost\\$distribution${linuxProjectDir.replace('/', '\\')}").also {
            check(Files.isDirectory(it)) { "$it is not reachable from Windows" }
        }
    }

    override fun installDependencies(projectDir: Path) {
        run("wsl.exe", "-d", distribution, "--cd", linuxProjectDir, "--", "npm", "install", "--no-audit", "--no-fund")
    }

    override val nodeInterpreter: String
        get() = "wsl://$distribution@${System.getenv("OXC_TEST_WSL_NODE")?.takeIf { it.isNotBlank() } ?: "/usr/bin/node"}"
}
