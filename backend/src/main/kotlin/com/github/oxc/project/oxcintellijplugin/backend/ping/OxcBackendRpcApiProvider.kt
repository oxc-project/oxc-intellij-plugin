@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.backend.ping

import com.github.oxc.project.oxcintellijplugin.shared.ping.OxcPingApi
import com.github.oxc.project.oxcintellijplugin.shared.ping.OxcPingResult
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class OxcBackendRpcApiProvider : RemoteApiProvider {
    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<OxcPingApi>()) {
            OxcPingApiImpl()
        }
    }
}

private class OxcPingApiImpl : OxcPingApi {
    override suspend fun ping(): OxcPingResult = OxcPingResult(ProcessHandle.current().pid())
}
