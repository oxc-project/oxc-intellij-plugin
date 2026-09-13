package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusNotifications
import com.intellij.execution.ExecutionException
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.LspServerSupportProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class OxcLspTool(val provider: Class<out LspServerSupportProvider>, val displayName: String) {
    OXLINT(OxlintLspServerSupportProvider::class.java, "Oxlint"),
    OXFMT(OxfmtLspServerSupportProvider::class.java, "Oxfmt");

    fun resolve(project: Project, file: VirtualFile): OxcServerCommand? {
        if (!file.isValid || !file.isInLocalFileSystem) return null
        return when (this) {
            OXLINT -> if (OxlintSettings.getInstance(project).fileSupported(file)) OxlintPackage(project).resolveCommand(file) else null
            OXFMT -> if (OxfmtSettings.getInstance(project).fileSupported(file)) OxfmtPackage(project).resolveCommand(file) else null
        }
    }

    fun descriptor(project: Project, command: OxcServerCommand): LspServerDescriptor = when (this) {
        OXLINT -> OxlintLspServerDescriptor(project, command)
        OXFMT -> OxfmtLspServerDescriptor(project, command)
    }
}

/**
 * The 2025.2 LSP manager permits ten servers per project and can stop only a whole provider.
 * Keep at most four recent scopes per tool. On eviction, rebuild that tool's retained scopes.
 * Selection and save operations reactivate evicted scopes; saves pin a running server or use their own session.
 */
@Service(Service.Level.PROJECT)
class OxcLspServerPool(private val project: Project, private val scope: CoroutineScope) {
    private class Pool {
        val mutex = Mutex()
        val commands = LinkedHashMap<VirtualFile, OxcServerCommand>()
        var pending: Job? = null
        val restartRevision = AtomicLong()
    }

    private val pools = OxcLspTool.entries.associateWith { Pool() }
    private val manager get() = LspServerManager.getInstance(project)
    internal val isIdle: Boolean get() = pools.values.all { pool -> synchronized(pool) { pool.pending?.isCompleted != false } }

    private val openedFiles = ConcurrentHashMap<LspServer, MutableSet<VirtualFile>>()

    init {
        manager.addLspServerManagerListener(object : LspServerManagerListener {
            override fun fileOpened(lspServer: LspServer, file: VirtualFile) {
                if (OxcLspTool.entries.any { it.provider == lspServer.providerClass }) {
                    openedFiles.computeIfAbsent(lspServer) { ConcurrentHashMap.newKeySet() }.add(file)
                }
            }

            override fun serverStateChanged(lspServer: LspServer) {
                if (lspServer.state == LspServerState.ShutdownNormally || lspServer.state == LspServerState.ShutdownUnexpectedly) {
                    openedFiles.remove(lspServer)
                }
            }
        }, project)
    }

    fun fileClosed(file: VirtualFile) {
        openedFiles.values.forEach { it.remove(file) }
    }

    fun fileOpened(tool: OxcLspTool, file: VirtualFile, allowEviction: Boolean = true) = enqueue(tool) {
        val command = resolve(tool, file) ?: return@enqueue
        val commands = pools.getValue(tool).commands
        // The IDE replays provider callbacks for open files. Those callbacks must not restore
        // evicted background scopes and trigger another cycle of server replacements.
        if (!allowEviction && command.root !in commands && commands.size >= capacity()) return@enqueue
        ensureServer(tool, command)
    }

    suspend fun <T> withServer(tool: OxcLspTool, file: VirtualFile, document: Document, action: suspend (LspServer) -> T): T? {
        val command = resolve(tool, file) ?: return null
        val pool = pools.getValue(tool)
        // A modal save must not wait for queued restarts, which may themselves need the non-modal EDT.
        if (pool.mutex.tryLock()) {
            try {
                val server = manager.getServersForProvider(tool.provider).firstOrNull {
                    it.state == LspServerState.Running && openedFiles[it]?.contains(file) == true &&
                        it.descriptor.roots.contentEquals(arrayOf(command.root))
                }
                if (server != null) return action(server)
            } finally {
                pool.mutex.unlock()
            }
        }
        // The IDE may defer didOpen until after the modal save task. Use the unsaved snapshot now.
        val descriptor = readAction { tool.descriptor(project, command) }
        val result = OxcSaveSession(project, tool, descriptor).run(file, document, action)
        fileOpened(tool, file)
        return result
    }

    fun restart(tool: OxcLspTool) {
        val pool = pools.getValue(tool)
        val revision = pool.restartRevision.incrementAndGet()
        enqueue(tool) {
            // A package installation can produce many VFS batches before a restart gets its turn.
            if (revision != pool.restartRevision.get()) return@enqueue
            val commands = pool.commands
            commands.clear()
            // Re-detect after settings, declarations or installations change. Prefer the selected tab.
            val files = readAction {
                val editors = FileEditorManager.getInstance(project)
                (editors.openFiles.toList() + editors.selectedFiles).distinct().sortedBy {
                    it in editors.selectedFiles
                }
            }
            for (file in files) {
                val command = resolve(tool, file) ?: continue
                commands.remove(command.root)
                commands[command.root] = command
            }
            trim(commands)
            if (!stopServers(tool)) return@enqueue
            for (command in commands.values) startServer(tool, command)
        }
    }

    fun stop(tool: OxcLspTool) = enqueue(tool) {
        pools.getValue(tool).commands.clear()
        stopServers(tool)
    }

    private fun enqueue(tool: OxcLspTool, action: suspend () -> Unit) {
        val pool = pools.getValue(tool)
        // Preserve event order without inheriting the caller's IDE read action.
        synchronized(pool) {
            val previous = pool.pending
            pool.pending = scope.launch {
                previous?.join()
                pool.mutex.withLock { action() }
            }
        }
    }

    private suspend fun resolve(tool: OxcLspTool, file: VirtualFile): OxcServerCommand? = readAction {
        if (TrustedProjects.isProjectTrusted(project)) tool.resolve(project, file) else null
    }

    private suspend fun ensureServer(tool: OxcLspTool, command: OxcServerCommand): LspServer? {
        val commands = pools.getValue(tool).commands
        val previous = commands.remove(command.root)
        commands[command.root] = command
        val evicted = trim(commands)
        if (evicted || previous != null && previous != command) {
            if (!stopServers(tool)) return null
            for (retained in commands.values) startServer(tool, retained)
        }
        return manager.getServersForProvider(tool.provider).firstOrNull { it.descriptor.roots.contentEquals(arrayOf(command.root)) }
            ?: startServer(tool, command)
    }

    private fun capacity(): Int {
        val otherServers = LspServerSupportProvider.EP_NAME.extensionList
            .map { it.javaClass }.filter { provider -> OxcLspTool.entries.none { it.provider == provider } }
            .sumOf { manager.getServersForProvider(it).size }
        return ((10 - otherServers) / 2).coerceIn(1, 4)
    }

    private fun trim(commands: LinkedHashMap<VirtualFile, OxcServerCommand>): Boolean {
        val capacity = capacity()
        var removed = false
        while (commands.size > capacity) {
            commands.remove(commands.keys.first())
            removed = true
        }
        return removed
    }

    private suspend fun stopServers(tool: OxcLspTool): Boolean {
        withContext(Dispatchers.EDT) { manager.stopServers(tool.provider) }
        // Wait for removal before submitting replacements to the IDE's asynchronous starter.
        return withTimeoutOrNull(30_000) {
            while (manager.getServersForProvider(tool.provider).isNotEmpty()) delay(10)
            true
        } == true
    }

    private suspend fun startServer(tool: OxcLspTool, command: OxcServerCommand): LspServer? {
        if (project.isDisposed || !scope.isActive) return null
        val descriptor = try {
            readAction { tool.descriptor(project, command) }
        } catch (e: ExecutionException) {
            if (command.vitePlus) {
                VitePlusNotifications.getInstance(project).launchFailed(command.root.path, tool.displayName)
            }
            thisLogger().warn("Cannot create ${tool.name} server for ${command.root.path}", e)
            return null
        }
        manager.ensureServerStarted(tool.provider, descriptor)
        return withTimeoutOrNull(30_000) {
            while (!project.isDisposed && scope.isActive) {
                manager.getServersForProvider(tool.provider)
                    .firstOrNull { it.descriptor.roots.contentEquals(descriptor.roots) && it.state != LspServerState.Initializing }
                    ?.let { return@withTimeoutOrNull it }
                delay(10)
            }
            null
        }
    }

    companion object {
        fun getInstance(project: Project): OxcLspServerPool = project.getService(OxcLspServerPool::class.java)
    }
}
