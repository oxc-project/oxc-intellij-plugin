@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.backend.rpc

import com.github.oxc.project.oxcintellijplugin.backend.files.BackendFolderLister
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.fix.OxlintFixAll
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettingsListener
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFolder
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class OxcBackendRpcApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OxlintServerApi>()) {
      OxlintServerApiImpl()
    }
    remoteApi(remoteApiDescriptor<BackendFilesApi>()) {
      BackendFilesApiImpl()
    }
    remoteApi(remoteApiDescriptor<OxlintSettingsApi>()) {
      OxlintSettingsApiImpl()
    }
  }
}

private class OxlintServerApiImpl : OxlintServerApi {

  override suspend fun restart(projectId: ProjectId) {
    val project = projectId.findProjectOrNull() ?: return
    withContext(Dispatchers.EDT) {
      LspClientManager.getInstance(project)
        .stopAndRestartClientsIfNeeded(OxlintLspIntegrationProvider::class.java)
    }
  }

  override suspend fun fixAll(projectId: ProjectId, file: VirtualFileId): OxlintFixAllResult {
    val project = projectId.findProjectOrNull() ?: return OxlintFixAllResult.NothingToApply
    val virtualFile = file.virtualFile() ?: return OxlintFixAllResult.NothingToApply
    return OxlintFixAll.getInstance(project).fixFile(virtualFile)
  }
}

private class BackendFilesApiImpl : BackendFilesApi {

  override suspend fun listFolder(projectId: ProjectId?, path: String?): BackendFolder =
    withContext(Dispatchers.IO) {
      BackendFolderLister(projectId?.findProjectOrNull()?.basePath).list(path)
    }
}

private class OxlintSettingsApiImpl : OxlintSettingsApi {

  override suspend fun load(projectId: ProjectId?): OxlintSettingsData =
    settingsProject(projectId)?.let { OxlintSettings.getInstance(it).toData() } ?: OxlintSettingsData()

  override suspend fun apply(projectId: ProjectId?, settings: OxlintSettingsData) {
    val project = settingsProject(projectId) ?: return
    OxlintSettings.getInstance(project).update(settings)
    if (!project.isDefault) SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
  }

  override suspend fun updates(projectId: ProjectId): Flow<OxlintSettingsData> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    val settings = OxlintSettings.getInstance(project)
    // Reads the settings when emitting, so that a value read earlier can't replace a newer change.
    return callbackFlow {
      val connection = project.messageBus.connect()
      connection.subscribe(OxlintSettingsListener.TOPIC, OxlintSettingsListener { trySend(Unit) })
      send(Unit)
      awaitClose { connection.disconnect() }
    }.conflate().map { settings.toData() }.distinctUntilChanged()
  }

  private fun settingsProject(projectId: ProjectId?): Project? =
    if (projectId == null) ProjectManager.getInstance().defaultProject else projectId.findProjectOrNull()
}
