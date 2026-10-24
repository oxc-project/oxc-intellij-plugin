package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.NioFiles
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

/**
 * Settings for tests that run language servers inside WSL. They run on Windows when
 * `OXC_TEST_WSL_DISTRO` names an installed distribution that has Node.js at `OXC_TEST_WSL_NODE`
 * (default `/usr/bin/node`), and are skipped otherwise.
 */
object WslTesting {

    private val distributionId: String? = System.getenv("OXC_TEST_WSL_DISTRO")?.takeIf { it.isNotBlank() }

    private val nodePath: String = System.getenv("OXC_TEST_WSL_NODE")?.takeIf { it.isNotBlank() } ?: "/usr/bin/node"

    val enabled: Boolean
        get() = SystemInfo.isWindows && distributionId != null

    fun distribution(): WSLDistribution =
        WslDistributionManager.getInstance().getOrCreateDistributionByMsId(distributionId!!)

    /**
     * Makes the IDE reach WSL files and processes through `\\wsl$` and `wsl.exe` instead of its
     * IJent agent: in tests, IJent needs a test-only class that the IDE does not ship.
     *
     * Call before `super.setUp()`, so before the IDE starts in this JVM. The build cannot pass
     * these as system properties: WebStorm's Windows launch arguments set
     * `wsl.use.remote.agent.for.nio.filesystem=true`, and the IntelliJ Platform Gradle Plugin adds
     * them after the build's own, so they win. Setting them at runtime replaces that value. A
     * registry value would also work, but changing it makes the IDE try to rewrite its vmoptions,
     * which logs an error in tests. This applies to the whole test JVM, which only runs WSL tests
     * when `OXC_TEST_WSL_DISTRO` is set.
     */
    fun disableIjent() {
        System.setProperty("wsl.use.remote.agent.for.nio.filesystem", "false")
        System.setProperty("wsl.use.remote.agent.for.launch.processes", "false")
    }

    /** The distribution's Node.js, as the project interpreter would be set in the IDE. */
    fun nodeInterpreterRef(): NodeJsInterpreterRef = NodeJsInterpreterRef.create(WslNodeInterpreter(distributionId!!, nodePath))

    /** Creates an empty directory inside the distribution and returns its Windows (`\\wsl...`) path. */
    fun createTempDir(distribution: WSLDistribution): Path =
        Files.createDirectories(onPooledThread { distribution.getUNCRootPath() }.resolve("tmp/oxc-test-${UUID.randomUUID()}"))

    fun deleteTempDir(dir: Path) {
        NioFiles.deleteRecursively(dir)
    }

    /**
     * Runs [block] off the EDT. Converting paths and starting processes may run `wsl.exe`, which
     * the IDE forbids on the EDT; the IDE does all of it on background threads.
     */
    fun <T> onPooledThread(block: () -> T): T =
        ApplicationManager.getApplication().executeOnPooledThread(Callable(block)).get(60, TimeUnit.SECONDS)
}
