@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.backend.rpc

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.backend.tool.OxcSettingsListener
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

internal class OxcBackendRpcApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OxlintSettingsApi>()) {
      OxlintSettingsApiImpl()
    }
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

private fun settingsProject(projectId: ProjectId?): Project? =
  if (projectId == null) ProjectManager.getInstance().defaultProject else projectId.findProjectOrNull()

private fun scheduleSave(project: Project) {
  if (!project.isDefault) SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
}

/**
 * Reads the settings when emitting, so that a value read earlier can't replace a newer change. Ends when
 * the plugin unloads or the project closes, rather than waiting for the collector, which can be a thin
 * client, to cancel.
 */
private fun <D : Any> settingsUpdates(project: Project, tool: OxcTool, read: () -> D): Flow<D> =
  callbackFlow {
    val lifetime = Disposer.newDisposable(OxcProjectDisposable.getInstance(project), "Oxc ${tool.name} settings updates")
    Disposer.register(lifetime) { close() }
    project.messageBus.connect(lifetime).subscribe(OxcSettingsListener.TOPIC, OxcSettingsListener { changedTool, _ ->
      if (changedTool == tool) trySend(Unit)
    })
    send(Unit)
    awaitClose { Disposer.dispose(lifetime) }
  }.conflate().map { read() }.distinctUntilChanged()
