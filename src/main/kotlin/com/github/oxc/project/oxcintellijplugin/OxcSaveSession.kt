package com.github.oxc.project.oxcintellijplugin

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer

/**
 * A cold save cannot wait for the IDE's didOpen: 2025.2 schedules it with non-modal modality,
 * while Actions on Save runs in a modal progress task. Use a short-lived LSP session with the
 * document snapshot, then close it. The regular editor server can warm up after the save.
 */
internal class OxcSaveSession(
    private val project: Project,
    private val tool: OxcLspTool,
    private val descriptor: LspServerDescriptor,
) {
    private var state = LspServerState.Initializing
    private var initializeResult: InitializeResult? = null
    private lateinit var remote: LanguageServer

    // New IDEs add LspClient methods and a covariant descriptor bridge to LspServer. Adapt the
    // runtime interface so the save session can support both that API and the 2025.2 SDK.
    @Suppress("UNCHECKED_CAST")
    private val server = Proxy.newProxyInstance(LspServer::class.java.classLoader, arrayOf(LspServer::class.java)) { proxy, method, args ->
        when (method.name) {
            "getProject" -> project
            "getProviderClass" -> tool.provider
            "getDescriptor" -> descriptor
            "getState" -> state
            "getInitializeResult" -> initializeResult
            "getDocumentIdentifier" -> TextDocumentIdentifier(descriptor.getFileUri(args[0] as VirtualFile))
            "getDocumentVersion", "nextDocumentVersion" -> documentVersion(args[0] as Document)
            "sendNotification" -> (args[0] as (LanguageServer) -> Unit)(remote)
            "sendRequestSync" -> (args[1] as (LanguageServer) -> CompletableFuture<Any?>)(remote)
                .get((args[0] as Int).toLong(), TimeUnit.MILLISECONDS)
            "sendRequest" -> {
                val request: suspend () -> Any? = { (args[0] as (LanguageServer) -> CompletableFuture<Any?>)(remote).await() }
                request.startCoroutineUninterceptedOrReturn(args[1] as Continuation<Any?>)
            }
            "equals" -> proxy === args[0]
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "${tool.displayName} save session"
            else -> error("Unsupported LSP session method: ${method.name}")
        }
    } as LspServer

    suspend fun <T> run(file: VirtualFile, document: Document, action: suspend (LspServer) -> T): T {
        val handler = withContext(Dispatchers.IO) { descriptor.startServerProcess() }
        val input = PipedInputStream(65536)
        val output = PipedOutputStream(input)
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.STDOUT) {
                    try {
                        output.write(event.text.toByteArray(Charsets.UTF_8))
                    } catch (_: IOException) {
                        // The process can still emit output while the session closes its pipe.
                    }
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                output.close()
            }
        })
        val client = object : LanguageClient {
            override fun telemetryEvent(value: Any?) = Unit
            override fun publishDiagnostics(params: PublishDiagnosticsParams) = Unit
            override fun showMessage(params: MessageParams) = Unit
            override fun logMessage(params: MessageParams) = Unit
            override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem> =
                CompletableFuture.completedFuture(null)
            override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any?>> =
                CompletableFuture.completedFuture(params.items.map { descriptor.getWorkspaceConfiguration(it) })
            override fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>> =
                CompletableFuture.completedFuture(descriptor.roots.map { WorkspaceFolder(descriptor.getFileUri(it), it.name) })
            // Registrations last only for this save; no editor or file watchers need to be installed.
            override fun registerCapability(params: RegistrationParams): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
            override fun unregisterCapability(params: UnregistrationParams): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
            override fun refreshDiagnostics(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        }
        val launcher = Launcher.Builder<LanguageServer>().setLocalService(client)
            .setRemoteInterface(LanguageServer::class.java).setInput(input)
            .setExecutorService(AppExecutorUtil.getAppExecutorService())
            .setOutput(handler.processInput!!).create()
        remote = launcher.remoteProxy
        val listening = launcher.startListening()
        handler.startNotify()
        try {
            val params = readAction { descriptor.createInitializeParams() }
            initializeResult = withTimeout(30_000) { remote.initialize(params).await() }
            remote.initialized(InitializedParams())
            val item = readAction {
                TextDocumentItem(descriptor.getFileUri(file), descriptor.getLanguageId(file), documentVersion(document), document.text)
            }
            remote.textDocumentService.didOpen(DidOpenTextDocumentParams(item))
            state = LspServerState.Running
            return action(server)
        } finally {
            withContext(NonCancellable) {
                withTimeoutOrNull(2_000) { runCatching { remote.shutdown().await() } }
                runCatching { remote.exit() }
                handler.destroyProcess()
                withContext(Dispatchers.IO) { handler.waitFor(5_000) }
                listening.cancel(true)
                input.close()
                output.close()
                state = LspServerState.ShutdownNormally
            }
        }
    }

    private fun documentVersion(document: Document): Int =
        (document as? DocumentEx)?.modificationSequence ?: document.modificationStamp.toInt()
}
