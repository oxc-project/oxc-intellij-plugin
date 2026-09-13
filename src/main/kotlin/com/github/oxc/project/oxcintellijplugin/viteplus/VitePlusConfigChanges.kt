package com.github.oxc.project.oxcintellijplugin.viteplus

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

fun VFileEvent.affectsVitePlusDiscovery(): Boolean {
    if (isVitePlusDiscoveryPath(path)) return true
    if (this is VFilePropertyChangeEvent && propertyName == VirtualFile.PROP_NAME) {
        return isVitePlusDiscoveryPath("${file.parent?.path}/$oldValue") ||
            isVitePlusDiscoveryPath("${file.parent?.path}/$newValue")
    }
    if (this is VFileMoveEvent) {
        return isVitePlusDiscoveryPath("${oldParent.path}/${file.name}") ||
            isVitePlusDiscoveryPath("${newParent.path}/${file.name}")
    }
    return false
}

internal fun isVitePlusDiscoveryPath(path: String): Boolean {
    val normalized = path.replace('\\', '/')
    val name = normalized.substringAfterLast('/')
    if (!normalized.contains("/node_modules/")) {
        return name in setOf("package.json", "pnpm-workspace.yaml", "lerna.json", "node_modules")
    }
    return normalized.endsWith("/node_modules/vite-plus") ||
        normalized.contains("/node_modules/vite-plus/") ||
        normalized.endsWith("/node_modules/.bin") ||
        normalized.substringAfterLast("/node_modules/.bin/", "") in setOf("vp", "vp.cmd", "vp.exe")
}
