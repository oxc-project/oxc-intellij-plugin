package com.github.oxc.project.oxcintellijplugin.lsp

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.lsp.api.LspServerListener
import org.eclipse.lsp4j.InitializeResult

/**
 * Applies [policy] to the lifecycle of the single language server whose descriptor holds this
 * listener.
 *
 * The platform reports a stop here whether it was requested through `LspServerManager` or the
 * server process died on its own, and `shutdownNormally` tells the two apart.
 */
class OxcLspServerRestartListener(
    private val toolName: String,
    private val policy: LspServerRestartPolicy,
    private val isToolEnabled: () -> Boolean,
    private val requestRestart: () -> Unit,
) : LspServerListener {

    override fun serverInitialized(params: InitializeResult) {
        policy.serverInitialized(this)
    }

    override fun serverStopped(shutdownNormally: Boolean) {
        when (policy.serverStopped(this, shutdownNormally, isToolEnabled())) {
            RestartDecision.RESTART -> {
                thisLogger().info("$toolName language server stopped unexpectedly, starting it again")
                requestRestart()
            }
            RestartDecision.RESTART_PENDING -> thisLogger().info(
                "$toolName language server stopped unexpectedly, a restart requested moments ago covers it")
            RestartDecision.BUDGET_EXHAUSTED -> thisLogger().warn(
                "$toolName language server restarted more than ${LspServerRestartPolicy.MAX_RESTARTS} times " +
                "within ${LspServerRestartPolicy.WINDOW}, not restarting it until the rate drops")
            RestartDecision.NONE -> Unit
        }
    }
}
