package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerDescriptor
import org.eclipse.lsp4j.InitializeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class OxcLspServerRestartListenerTest {

    private val timeSource = TestTimeSource()

    private var toolEnabled = true

    private var restartRequests = 0

    private val policy = LspServerRestartPolicy(maxRestarts = 3, window = 1.seconds,
        coalesceWindow = 100.milliseconds, timeSource = timeSource)

    @Test
    fun killedServer_isStartedAgain() {
        // https://github.com/oxc-project/oxc-intellij-plugin/issues/425
        val listener = startedServer()

        listener.serverStopped(false)

        assertEquals(1, restartRequests)
    }

    @Test
    fun requestedShutdown_startsNothing() {
        val listener = startedServer()

        listener.serverStopped(true)

        assertEquals(0, restartRequests)
    }

    @Test
    fun stopOfADisabledTool_startsNothing() {
        val listener = startedServer()
        toolEnabled = false

        listener.serverStopped(false)

        assertEquals(0, restartRequests)
    }

    @Test
    fun furtherServersOfOneBurst_startNothingMore() {
        val listeners = List(3) { startedServer() }

        listeners.forEach { it.serverStopped(false) }

        assertEquals(1, restartRequests)
    }

    @Test
    fun stopBeyondTheBudget_startsNothing() {
        repeat(3) {
            timeSource += 100.milliseconds
            startedServer().serverStopped(false)
        }
        timeSource += 100.milliseconds

        startedServer().serverStopped(false)

        assertEquals(3, restartRequests)
    }

    @Test
    fun bothServerDescriptors_carryARestartListener() {
        assertNotNull(OxlintLspServerDescriptor::class.java.getDeclaredMethod("getLspServerListener"))
        assertNotNull(OxfmtLspServerDescriptor::class.java.getDeclaredMethod("getLspServerListener"))
    }

    private fun startedServer(): OxcLspServerRestartListener {
        val listener = OxcLspServerRestartListener("Oxlint", policy, { toolEnabled },
            { restartRequests++ })
        listener.serverInitialized(InitializeResult())
        return listener
    }
}
