package com.github.oxc.project.oxcintellijplugin.lsp

import java.util.WeakHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Decides whether a language server that stopped has to be started again.
 *
 * Semantics:
 * 1. A stop that neither the plugin nor the user requested is answered with
 *    [RestartDecision.RESTART].
 * 2. Only a server that reached the initialized state is restarted, so a start that fails is not
 *    retried through this policy.
 * 3. At most [maxRestarts] restarts are granted per [window]; beyond that the decision is
 *    [RestartDecision.BUDGET_EXHAUSTED] until the earlier restarts fall out of the window. A server
 *    that was already initialized when a restart was granted less than [coalesceWindow] ago is
 *    started again by that restart, so its stop is neither granted nor charged.
 * 4. A stop reported as a normal shutdown is not restarted.
 * 5. oxlint and oxfmt are decided on separately.
 * 6. A stop reported while the tool is disabled in the settings is not restarted.
 *
 * One instance covers all the servers of a single tool in a single project, and a restart it grants
 * restarts all of them. It is safe to call from any thread.
 */
class LspServerRestartPolicy(
    private val maxRestarts: Int = MAX_RESTARTS,
    private val window: Duration = WINDOW,
    private val coalesceWindow: Duration = COALESCE_WINDOW,
    // The source is monotonic because both windows are elapsed times, which a wall clock measures
    // wrong whenever it steps, for instance on an NTP correction or a resume from suspend.
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {

    /**
     * When each server completed its initialization, which decides whether a restart granted
     * afterwards covers it. The map is weak so that it never holds a key longer than the caller
     * holds the server it stands for.
     */
    private val initializationMarks: MutableMap<Any, ComparableTimeMark> = WeakHashMap()

    private val grantedRestarts = ArrayDeque<ComparableTimeMark>()

    /**
     * Records that the server denoted by [serverKey] finished its initialization handshake.
     *
     * [serverKey] stands for a single server of the tool and is compared by identity.
     */
    @Synchronized
    fun serverInitialized(serverKey: Any) {
        initializationMarks[serverKey] = timeSource.markNow()
    }

    /** Returns the decision for the server denoted by [serverKey] having stopped. */
    @Synchronized
    fun serverStopped(serverKey: Any, shutdownNormally: Boolean, toolEnabled: Boolean): RestartDecision {
        val initializedAt = initializationMarks.remove(serverKey)
        if (shutdownNormally || initializedAt == null || !toolEnabled) {
            return RestartDecision.NONE
        }

        val now = timeSource.markNow()
        val lastRestart = grantedRestarts.lastOrNull()
        // A server that initialized after the last restart was granted cannot be covered by it,
        // so its stop is a new incident rather than part of the one already answered.
        if (lastRestart != null && now - lastRestart < coalesceWindow && initializedAt <= lastRestart) {
            return RestartDecision.RESTART_PENDING
        }

        while (grantedRestarts.isNotEmpty() && now - grantedRestarts.first() >= window) {
            grantedRestarts.removeFirst()
        }
        if (grantedRestarts.size >= maxRestarts) {
            return RestartDecision.BUDGET_EXHAUSTED
        }
        grantedRestarts.addLast(now)
        return RestartDecision.RESTART
    }

    companion object {

        // A tool killed from outside the IDE costs a single restart, while a tool whose servers die
        // right after every start exhausts the budget within seconds and is only restarted again
        // once the window has passed.
        const val MAX_RESTARTS = 3
        val WINDOW = 1.minutes

        // Killing the servers of one tool terminates them within a moment of each other, and the
        // restart of the first one covers the rest.
        val COALESCE_WINDOW = 5.seconds
    }
}

enum class RestartDecision {
    /** Nothing to do: the stop was requested, the server never ran, or the tool is disabled. */
    NONE,

    /** All the servers of the tool have to be started again, and the restart has been charged. */
    RESTART,

    /** A restart granted moments ago starts this server again too, so it needs no other. */
    RESTART_PENDING,

    /** The servers stopped unexpectedly, but too often to keep restarting them. */
    BUDGET_EXHAUSTED,
}
