package com.github.oxc.project.oxcintellijplugin.extensions

import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.openapi.vfs.VirtualFile

class FileExtTest : OxcLightTestCase() {

    fun testOxlintConfigFiles() {
        assertTrue(file(".oxlintrc.json").isOxlintJsonConfigFile())
        assertTrue(file(".oxlintrc.jsonc").isOxlintJsonConfigFile())
        assertFalse(file("oxlint.config.ts").isOxlintJsonConfigFile())

        assertTrue(file(".oxlintrc.json").isOxlintConfigFile())
        assertTrue(file(".oxlintrc.jsonc").isOxlintConfigFile())
        assertTrue(file("oxlint.config.ts").isOxlintConfigFile())

        assertFalse(file("oxlintrc.json").isOxlintConfigFile())
        assertFalse(file(".oxlintrc").isOxlintConfigFile())
        assertFalse(file(".oxlintrc.json5").isOxlintConfigFile())
        assertFalse(file("oxlint.config.js").isOxlintConfigFile())
        assertFalse(file(".oxfmtrc.json").isOxlintConfigFile())
    }

    fun testOxfmtConfigFiles() {
        assertTrue(file(".oxfmtrc.json").isOxfmtJsonConfigFile())
        assertTrue(file(".oxfmtrc.jsonc").isOxfmtJsonConfigFile())
        assertFalse(file("oxfmt.config.ts").isOxfmtJsonConfigFile())

        assertTrue(file(".oxfmtrc.json").isOxfmtConfigFile())
        assertTrue(file(".oxfmtrc.jsonc").isOxfmtConfigFile())
        assertTrue(file("oxfmt.config.ts").isOxfmtConfigFile())

        assertFalse(file(".oxfmtrc").isOxfmtConfigFile())
        assertFalse(file("oxfmt.config.mjs").isOxfmtConfigFile())
        assertFalse(file(".oxlintrc.json").isOxfmtConfigFile())
    }

    fun testViteConfigFiles() {
        for (extension in listOf("ts", "mts", "cts", "js", "mjs", "cjs")) {
            assertTrue(extension, file("vite.config.$extension").isViteConfigFile())
        }
        assertFalse(file("vite.config.json").isViteConfigFile())
        assertFalse(file("vitest.config.ts").isViteConfigFile())
    }

    fun testFindNearestOxfmtConfigPrefersTheClosestDirectory() {
        val root = file(".oxfmtrc.json")
        val nested = file("packages/app/.oxfmtrc.jsonc")
        val rootDir = root.parent

        assertEquals(nested, file("packages/app/src/index.js").findNearestOxfmtJsonConfigFile(rootDir))
        assertEquals(root, file("packages/lib/index.js").findNearestOxfmtJsonConfigFile(rootDir))
        assertEquals(root, file("index.js").findNearestOxfmtJsonConfigFile(rootDir))
    }

    fun testFindNearestOxfmtConfigStopsAtTheGivenRoot() {
        file(".oxfmtrc.json")
        val packageRoot = file("packages/app/package.json").parent

        assertNull(file("packages/app/src/index.js").findNearestOxfmtJsonConfigFile(packageRoot))
    }

    fun testFindNearestOxfmtConfigIgnoresTypeScriptConfigs() {
        file("oxfmt.config.ts")
        val index = file("index.js")

        assertNull(index.findNearestOxfmtJsonConfigFile(index.parent))
    }

    /**
     * QUESTIONABLE: without a root (the default argument, and what callers pass when
     * `Project.guessProjectDir()` is null) nothing is ever found, because the search only
     * continues while the directory is under the set `{null}`. Callers would reasonably expect
     * "no root" to mean "search all the way up".
     */
    fun testFindNearestOxfmtConfigWithoutRootFindsNothing() {
        file(".oxfmtrc.json")

        assertNull(file("src/index.js").findNearestOxfmtJsonConfigFile())
    }

    private fun file(path: String): VirtualFile =
        myFixture.findFileInTempDir(path) ?: myFixture.addFileToProject(path, "{}").virtualFile
}
