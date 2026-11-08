@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.backend.rpc

import com.github.oxc.project.oxcintellijplugin.backend.files.BackendFolderLister
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle.importOxfmtCodeStyle
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcFixAll
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFolder
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.settings.OxfmtSettingsData
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
    remoteApi(remoteApiDescriptor<OxcToolApi>()) {
      OxcToolApiImpl()
    }
    remoteApi(remoteApiDescriptor<BackendFilesApi>()) {
      BackendFilesApiImpl()
    }
    remoteApi(remoteApiDescriptor<OxlintSettingsApi>()) {
      OxlintSettingsApiImpl()
    }
    remoteApi(remoteApiDescriptor<OxfmtSettingsApi>()) {
      OxfmtSettingsApiImpl()
    }
    remoteApi(remoteApiDescriptor<OxfmtCodeStyleApi>()) {
      OxfmtCodeStyleApiImpl()
    }
  }
}

private class OxcToolApiImpl : OxcToolApi {

  override suspend fun restart(projectId: ProjectId, tool: OxcTool) {
    val project = projectId.findProjectOrNull() ?: return
    withContext(Dispatchers.EDT) {
      LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(ToolDefinition.of(tool).providerClass)
    }
  }

  override suspend fun fixAll(projectId: ProjectId, tool: OxcTool, file: VirtualFileId): OxcFixAllResult {
    val project = projectId.findProjectOrNull() ?: return OxcFixAllResult.NothingToApply
    val virtualFile = file.virtualFile() ?: return OxcFixAllResult.NothingToApply
    return OxcFixAll.getInstance(project).fixFile(ToolDefinition.of(tool), virtualFile)
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
    scheduleSave(project)
  }

  override suspend fun updates(projectId: ProjectId): Flow<OxlintSettingsData> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return settingsUpdates(project, OxcTool.OXLINT) { OxlintSettings.getInstance(project).toData() }
  }
}

private class OxfmtSettingsApiImpl : OxfmtSettingsApi {

  override suspend fun load(projectId: ProjectId?): OxfmtSettingsData =
    settingsProject(projectId)?.let { OxfmtSettings.getInstance(it).toData() } ?: OxfmtSettingsData()

  override suspend fun apply(projectId: ProjectId?, settings: OxfmtSettingsData) {
    val project = settingsProject(projectId) ?: return
    OxfmtSettings.getInstance(project).update(settings)
    scheduleSave(project)
  }

  override suspend fun updates(projectId: ProjectId): Flow<OxfmtSettingsData> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return settingsUpdates(project, OxcTool.OXFMT) { OxfmtSettings.getInstance(project).toData() }
  }
}

private class OxfmtCodeStyleApiImpl : OxfmtCodeStyleApi {

  override suspend fun importCodeStyle(projectId: ProjectId, file: VirtualFileId): OxfmtImportResult {
    val project = projectId.findProjectOrNull() ?: return OxfmtImportResult.CONFIG_NOT_FOUND
    val virtualFile = file.virtualFile() ?: return OxfmtImportResult.CONFIG_NOT_FOUND
    return importOxfmtCodeStyle(project, virtualFile)
  }
}

private fun settingsProject(projectId: ProjectId?): Project? =
  if (projectId == null) ProjectManager.getInstance().defaultProject else projectId.findProjectOrNull()

private fun scheduleSave(project: Project) {
  if (!project.isDefault) SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
}

/** Reads the settings when emitting, so that a value read earlier can't replace a newer change. */
private fun <D : Any> settingsUpdates(project: Project, tool: OxcTool, read: () -> D): Flow<D> =
  callbackFlow {
    val connection = project.messageBus.connect()
    connection.subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { changedTool, _ ->
      if (changedTool == tool) trySend(Unit)
    })
    send(Unit)
    awaitClose { connection.disconnect() }
  }.conflate().map { read() }.distinctUntilChanged()
