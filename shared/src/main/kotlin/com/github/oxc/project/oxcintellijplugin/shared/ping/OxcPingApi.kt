@file:Suppress("UnstableApiUsage")

package com.github.oxc.project.oxcintellijplugin.shared.ping

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable

/**
 * Throwaway RPC that proves the frontend can reach the backend. Remove it when the first real RPC
 * interface is added.
 */
@Rpc
interface OxcPingApi : RemoteApi<Unit> {
    suspend fun ping(): OxcPingResult

    companion object {
        suspend fun getInstance(): OxcPingApi =
            RemoteApiProviderService.resolve(remoteApiDescriptor<OxcPingApi>())
    }
}

@Serializable
data class OxcPingResult(val backendProcessId: Long)
