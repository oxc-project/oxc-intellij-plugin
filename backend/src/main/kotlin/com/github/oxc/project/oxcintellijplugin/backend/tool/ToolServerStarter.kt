package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Starts each tool's servers for the files open in every client.
 *
 * A new server only gets `didOpen` for the files that `openFiles` lists when it starts, and `openFiles` lists
 * the current client's files. So the platform's own restart, `stopAndRestartClientsIfNeeded`, run from a
 * backend event, finds no file of a thin client's, and a start from here has to run as each client. Nothing
 * public lists the clients, so this remembers the ones that opened a file.
 */
@Service(Service.Level.PROJECT)
class ToolServerStarter(private val project: Project, private val scope: CoroutineScope) {

  private val clients: MutableSet<ClientId> = ConcurrentHashMap.newKeySet()

  /** Called as the client that opened a file. */
  fun clientOpenedFile() {
    clients.add(ClientId.current)
  }

  /**
   * Stops the tool's servers and starts them again for the files open in every client. On the UI thread,
   * because a stop from elsewhere waits for a server that is still starting. The stop removes the old
   * clients before it returns, so the new ones don't count as already running.
   */
  @RequiresEdt
  fun restart(definition: ToolDefinition) {
    LspClientManager.getInstance(project).stopClients(definition.providerClass)
    startForOpenFiles(listOf(definition))
  }

  fun startForOpenFiles(definitions: List<ToolDefinition> = ToolDefinition.all) {
    // The caller's client first: a change from a client's settings page arrives as that client.
    val clientIds = linkedSetOf(ClientId.current, ClientId.localId) + clients
    scope.launch {
      if (!TrustedProjects.isProjectTrusted(project)) return@launch
      for (clientId in clientIds) {
        withContext(clientId.asContextElement()) { startForClient(clientId, definitions) }
      }
    }
  }

  /**
   * A server serves every client, so a second client with files under the same root only gets `didOpen`
   * once it opens or selects a file.
   */
  private suspend fun startForClient(clientId: ClientId, definitions: List<ToolDefinition>) {
    val files = readAction { FileEditorManager.getInstance(project).openFiles.toList() }
    // A client that left, or closed its files, lists none. It's added again when it opens one.
    if (files.isEmpty()) clients.remove(clientId)

    val manager = LspClientManager.getInstance(project)
    for (definition in definitions) {
      val settings = definition.settings(project)
      for (file in files.filter(settings::isSupported)) {
        // The same as opening the file: a server already running for it is kept.
        val descriptor = readAction { definition.createDescriptor(project, file) } ?: continue
        manager.ensureClientStarted(definition.providerClass, descriptor)
      }
    }
  }
}
