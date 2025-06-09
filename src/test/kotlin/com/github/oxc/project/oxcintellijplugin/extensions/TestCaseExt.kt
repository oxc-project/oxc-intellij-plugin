package com.github.oxc.project.oxcintellijplugin.extensions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun CodeInsightTestFixture.configureByFileAndCheckLanguageServerHighlighting(filePath: String) {
    val configuredFile = this.configureByFile(filePath).virtualFile
    val expectedHighlightingData = ExpectedHighlightingData(editor.document, true, true, true,
        false)
    expectedHighlightingData.init()
    this.waitForLanguageServerDiagnostics(this.project, configuredFile)

    (this as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(
        expectedHighlightingData)
}

fun CodeInsightTestFixture.waitForLanguageServerDiagnostics(project: Project, fileNeedingDiagnostics: VirtualFile,
    timeout: Duration = 10.seconds) {
    val diagnosticsReceived = AtomicBoolean()
    val shutdownReceived = AtomicBoolean()

    val disposable = Disposer.newDisposable()
    try {
        LspServerManager.getInstance(project)
            .addLspServerManagerListener(object : LspServerManagerListener {
                override fun serverStateChanged(lspServer: LspServer) {
                    if (lspServer.state in arrayOf(LspServerState.ShutdownNormally,
                            LspServerState.ShutdownUnexpectedly)
                    ) {
                        shutdownReceived.set(true)
                    }
                }

                override fun diagnosticsReceived(lspServer: LspServer, file: VirtualFile) {
                    if (file != fileNeedingDiagnostics) {
                        return
                    }
                    diagnosticsReceived.set(true)
                }
            }, disposable, true)

        PlatformTestUtil.waitWithEventsDispatching(
            "Timeout waiting for diagnostics for $fileNeedingDiagnostics",
            { diagnosticsReceived.get() || shutdownReceived.get() },
            timeout.inWholeSeconds.toInt())
    } finally {
        Disposer.dispose(disposable)
    }
}
