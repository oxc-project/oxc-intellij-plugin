@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.backend.rpc

import com.github.oxc.project.oxcintellijplugin.backend.files.BackendFolderLister
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFilesApi
import com.github.oxc.project.oxcintellijplugin.shared.files.BackendFolder
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.OxlintServerApi
import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OxcBackendRpcApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<OxlintServerApi>()) {
      OxlintServerApiImpl()
    }
    remoteApi(remoteApiDescriptor<BackendFilesApi>()) {
      BackendFilesApiImpl()
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
}

private class BackendFilesApiImpl : BackendFilesApi {

  override suspend fun listFolder(projectId: ProjectId?, path: String?): BackendFolder =
    withContext(Dispatchers.IO) {
      BackendFolderLister(projectId?.findProjectOrNull()?.basePath).list(path)
    }
}
