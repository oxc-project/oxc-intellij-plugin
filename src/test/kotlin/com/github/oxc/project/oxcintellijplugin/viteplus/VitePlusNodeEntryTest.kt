package com.github.oxc.project.oxcintellijplugin.viteplus

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VitePlusNodeEntryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val root get() = temporaryFolder.root.toPath()
    private fun write(name: String, content: String): Path = root.resolve(name).also {
        Files.createDirectories(it.parent)
        Files.writeString(it, content)
    }

    @Test fun `Node shebangs use the selected interpreter`() {
        for (shebang in listOf("#!/usr/bin/env node", "#!/usr/bin/env -S node", "#!/opt/node/bin/node")) {
            val entry = write("vp", "$shebang\n")
            assertEquals(entry, VitePlusNodeEntry.resolve(entry))
        }
    }

    @Test fun `pnpm global shim resolves its recorded target including paths with spaces`() {
        val entry = write("global store/vite-plus/bin/vp", "#!/usr/bin/env node\n")
        val shim = write("bin/vp", "#!/bin/sh\nexec node \"\$basedir/../global store/vite-plus/bin/vp\" \"\$@\"\n")
        assertEquals(entry, VitePlusNodeEntry.resolve(shim))
    }

    @Test fun `symlink to a global shim uses its real directory`() {
        val entry = write("store/vite-plus/bin/vp", "#!/usr/bin/env node\n")
        val shim = write("bin/vp", "#!/bin/sh\nexec node \"\$basedir/../store/vite-plus/bin/vp\" \"\$@\"\n")
        val link = root.resolve("vp")
        Files.createSymbolicLink(link, shim)
        assertEquals(entry.toRealPath(), VitePlusNodeEntry.resolve(link))
    }

    @Test fun `npm and pnpm cmd targets are resolved without running the shim`() {
        val entry = write("node_modules/vite-plus/bin/vp", "#!/usr/bin/env node\n")
        for (prefix in listOf("%~dp0", "%dp0%")) {
            val shim = write("vp.cmd", "@ECHO off\nnode \"$prefix\\node_modules\\vite-plus\\bin\\vp\" %*\n")
            assertEquals(entry, VitePlusNodeEntry.resolve(shim))
        }
    }

    @Test fun `native executables and unrelated shell scripts remain native`() {
        val native = write("vp", "native binary\n")
        assertNull(VitePlusNodeEntry.resolve(native))
        val shell = write("vp", "#!/bin/sh\necho hi\n")
        assertNull(VitePlusNodeEntry.resolve(shell))
    }

    @Test fun `missing and non Node shim targets are not run with Node`() {
        val shim = write("bin/vp", "#!/bin/sh\nexec node \"\$basedir/../entry\" \"\$@\"\n")
        assertNull(VitePlusNodeEntry.resolve(shim))
        write("entry", "#!/bin/sh\necho hi\n")
        assertNull(VitePlusNodeEntry.resolve(shim))
    }
}
