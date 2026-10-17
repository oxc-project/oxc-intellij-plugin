package com.github.oxc.project.oxcintellijplugin.lsp

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class LspServerRestartPolicyTest {

    private val timeSource = TestTimeSource()

    private val policy = LspServerRestartPolicy(maxRestarts = 2, window = 1.seconds,
        coalesceWindow = 100.milliseconds, timeSource = timeSource)

    @Test
    fun initializedServer_stoppingUnexpectedly_isGrantedARestart() {
        // https://github.com/oxc-project/oxc-intellij-plugin/issues/425
        val server = Any()
        policy.serverInitialized(server)

        assertEquals(RestartDecision.RESTART,
            policy.serverStopped(server, shutdownNormally = false, toolEnabled = true))
    }

    @Test
    fun serverThatFailedToInitialize_isRefused() {
        assertEquals(RestartDecision.NONE,
            policy.serverStopped(Any(), shutdownNormally = false, toolEnabled = true))
    }

    @Test
    fun initializedServer_shuttingDownNormally_isRefused() {
        val server = Any()
        policy.serverInitialized(server)

        assertEquals(RestartDecision.NONE,
            policy.serverStopped(server, shutdownNormally = true, toolEnabled = true))
    }

    @Test
    fun serverOfDisabledTool_isRefused() {
        val server = Any()
        policy.serverInitialized(server)

        assertEquals(RestartDecision.NONE,
            policy.serverStopped(server, shutdownNormally = false, toolEnabled = false))
    }

    @Test
    fun initializationOfOneServer_doesNotAuthorizeARestartForAnother() {
        policy.serverInitialized(Any())

        assertEquals(RestartDecision.NONE,
            policy.serverStopped(Any(), shutdownNormally = false, toolEnabled = true))
    }

    @Test
    fun serversKilledInOneBurst_shareASingleRestart() {
        val servers = List(3) { Any() }
        servers.forEach { policy.serverInitialized(it) }

        val decisions = servers.map {
            timeSource += 10.milliseconds
            policy.serverStopped(it, shutdownNormally = false, toolEnabled = true)
        }

        assertEquals(listOf(RestartDecision.RESTART, RestartDecision.RESTART_PENDING,
            RestartDecision.RESTART_PENDING), decisions)
    }

    @Test
    fun serverStartedByARestart_isGrantedItsOwnWhenKilledInTurn() {
        val killed = Any()
        policy.serverInitialized(killed)
        policy.serverStopped(killed, shutdownNormally = false, toolEnabled = true)

        timeSource += 20.milliseconds
        val replacement = Any()
        policy.serverInitialized(replacement)
        timeSource += 20.milliseconds

        assertEquals(RestartDecision.RESTART,
            policy.serverStopped(replacement, shutdownNormally = false, toolEnabled = true))
    }

    @Test
    fun burstOfStops_chargesASingleRestart() {
        val servers = List(4) { Any() }
        servers.forEach { policy.serverInitialized(it) }
        servers.forEach { policy.serverStopped(it, shutdownNormally = false, toolEnabled = true) }

        timeSource += 100.milliseconds

        assertEquals(RestartDecision.RESTART, runThenKillServer())
    }

    @Test
    fun restartsBeyondTheBudget_areRefused() {
        repeat(2) { assertEquals(RestartDecision.RESTART, runThenKillServer()) }

        assertEquals(RestartDecision.BUDGET_EXHAUSTED, runThenKillServer())
    }

    @Test
    fun budgetIsGrantedAgain_onceTheWindowHasPassed() {
        repeat(2) { runThenKillServer() }
        assertEquals(RestartDecision.BUDGET_EXHAUSTED, runThenKillServer())

        timeSource += 1.seconds

        assertEquals(RestartDecision.RESTART, runThenKillServer())
    }

    /** Stops a server outside the coalescing window of the previous restart. */
    private fun runThenKillServer(): RestartDecision {
        timeSource += 100.milliseconds
        val server = Any()
        policy.serverInitialized(server)
        return policy.serverStopped(server, shutdownNormally = false, toolEnabled = true)
    }
}
