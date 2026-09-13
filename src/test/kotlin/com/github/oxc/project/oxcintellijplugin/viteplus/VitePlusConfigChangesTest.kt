package com.github.oxc.project.oxcintellijplugin.viteplus

import org.junit.Assert.*
import org.junit.Test

class VitePlusConfigChangesTest {
    @Test fun `declarations and workspace boundaries trigger discovery`() {
        for (file in listOf("package.json", "pnpm-workspace.yaml", "lerna.json")) {
            assertTrue(isVitePlusDiscoveryPath("/repo/packages/app/$file"))
        }
    }

    @Test fun `install and shim changes trigger discovery`() {
        for (path in listOf("node_modules", "node_modules/.bin", "node_modules/.bin/vp",
            "node_modules/.bin/vp.cmd", "node_modules/.bin/vp.exe", "node_modules/vite-plus",
            "node_modules/vite-plus/package.json", "node_modules/vite-plus/bin/vp",
            "node_modules/.pnpm/vite-plus@0.3.1/node_modules/vite-plus/bin/vp")) {
            assertTrue(path, isVitePlusDiscoveryPath("/repo/$path"))
            assertTrue(path, isVitePlusDiscoveryPath("C:\\repo\\" + path.replace('/', '\\')))
        }
    }

    @Test fun `ordinary source and unrelated dependency changes do not restart servers`() {
        for (path in listOf("src/index.ts", "node_modules/other/package.json", "node_modules/.bin/other")) {
            assertFalse(path, isVitePlusDiscoveryPath("/repo/$path"))
        }
    }
}
