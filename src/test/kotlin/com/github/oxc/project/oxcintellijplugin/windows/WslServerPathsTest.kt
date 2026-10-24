package com.github.oxc.project.oxcintellijplugin.windows

import com.github.oxc.project.oxcintellijplugin.ServerPathsTest
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting.onPooledThread
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import java.net.URI
import java.nio.file.Path

/**
 * The fake server runs inside WSL with the distribution's Node.js, for a project on the Windows
 * side: it must be given WSL paths, and its edits must be applied to the Windows files. Runs on
 * Windows when WSL tests are configured (see [WslTesting]); CI runs it in the Windows test job.
 */
class WslServerPathsTest : ServerPathsTest() {

    private lateinit var distribution: WSLDistribution
    private lateinit var wslDir: Path

    override val fakeServerDir: Path
        get() = wslDir

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
            super.tearDown()
        } finally {
            // setUp may have failed before creating the directory.
            if (::wslDir.isInitialized) WslTesting.deleteTempDir(wslDir)
        }
    }

    /** The server must be given the path inside WSL, such as `/mnt/c/...` for a file on `C:`. */
    override fun assertUriNames(path: String, uri: String, message: String) {
        val wslPath = onPooledThread { distribution.getWslPath(Path.of(path)) }
            ?: error("$path has no path inside ${distribution.msId}")
        assertEquals(message, wslPath, URI(uri).path.removeSuffix("/"))
    }
}
