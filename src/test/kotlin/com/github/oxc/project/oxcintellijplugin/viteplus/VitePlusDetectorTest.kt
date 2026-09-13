package com.github.oxc.project.oxcintellijplugin.viteplus

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VitePlusDetectorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val repo get() = temporaryFolder.root.toPath().resolve("repo")
    private val detector = VitePlusDetector(windows = false)

    private fun write(path: String, content: String = ""): Path = repo.resolve(path).also {
        Files.createDirectories(it.parent)
        Files.writeString(it, content)
    }

    private fun declare(directory: String = "", field: String = "devDependencies") =
        write("${directory}package.json", """{"$field":{"vite-plus":"^0.3.1"}}""")

    private fun install(directory: String = ""): Path {
        write("${directory}node_modules/vite-plus/package.json", """{"name":"vite-plus","bin":{"vp":"bin/vp"}}""")
        return write("${directory}node_modules/vite-plus/bin/vp", "#!/usr/bin/env node\n")
    }

    @Test fun `root-declared-and-installed`() {
        declare(field = "dependencies")
        val vp = install()
        assertEquals(VitePlusProject(repo, vp), detector.detect(write("src/index.ts")))
    }

    @Test fun `pnpm-subpackage-declared-root-hoisted`() {
        write("pnpm-workspace.yaml", "packages: [packages/*]")
        declare("packages/app/")
        val vp = install()
        assertEquals(VitePlusProject(repo.resolve("packages/app"), vp), detector.detect(write("packages/app/src/index.ts")))
    }

    @Test fun `npm-subpackage-direct-dep-unhoisted`() {
        write("package.json", """{"workspaces":["packages/*"]}""")
        declare("packages/app/")
        val vp = install("packages/app/")
        assertEquals(VitePlusProject(repo.resolve("packages/app"), vp), detector.detect(repo.resolve("packages/app")))
    }

    @Test fun `root-declared-no-local-no-global`() {
        declare()
        assertEquals(VitePlusProject(repo, null), detector.detect(repo))
    }

    @Test fun `root-declared-no-local-global-on-path`() {
        declare()
        val global = temporaryFolder.newFile("vp").toPath()
        assertEquals(VitePlusProject(repo, global), detector.detect(repo, globalVp = { global }))
    }

    @Test fun `transitive-install`() {
        write("package.json", "{}")
        install()
        assertNull(detector.detect(repo, globalVp = { fail("Global lookup must be gated by declaration"); null }))
    }

    @Test fun `global-vp-without-declaration`() {
        write("package.json", "{}")
        assertNull(detector.detect(repo, globalVp = { fail("Not a Vite+ project"); null }))
    }

    @Test fun `parent-vite-plus-nested-repo`() {
        declare()
        install()
        for (marker in listOf("pnpm-workspace.yaml", "lerna.json", "package.json")) {
            write("nested/$marker", if (marker == "package.json") """{"workspaces":[]}""" else "{}")
            assertNull(detector.detect(write("nested/src/index.ts")))
            Files.delete(repo.resolve("nested/$marker"))
        }
    }

    @Test fun `plain-non-vite-plus`() {
        write("package.json", """{"devDependencies":{"oxlint":"1.82.0"}}""")
        assertNull(detector.detect(repo))
    }

    @Test fun `yarn4-pnp`() {
        declare()
        write(".pnp.cjs", "throw new Error('Never execute project code during detection')")
        assertEquals(VitePlusProject(repo, null), detector.detect(repo))
    }

    @Test fun `local lookup stops at workspace boundary before global fallback`() {
        install()
        write("nested/pnpm-workspace.yaml")
        declare("nested/")
        assertEquals(VitePlusProject(repo.resolve("nested"), null), detector.detect(repo.resolve("nested")))
        val global = temporaryFolder.newFile("vp").toPath()
        assertEquals(global, detector.detect(repo.resolve("nested"), globalVp = { global })?.vpPath)
    }

    @Test fun `local command takes priority over global and follows symlinks`() {
        declare()
        val vp = install()
        Files.createDirectories(repo.resolve("node_modules/.bin"))
        Files.createSymbolicLink(repo.resolve("node_modules/.bin/vp"), Path.of("../vite-plus/bin/vp"))
        assertEquals(vp, detector.detect(repo, globalVp = { fail("Must prefer local"); null })?.vpPath)
    }

    @Test fun `only direct dependencies qualify`() {
        for (json in listOf("null", "[]", "broken json", """{"peerDependencies":{"vite-plus":"*"}}""",
            """{"optionalDependencies":{"vite-plus":"*"}}""", """{"dependencies":null}""")) {
            write("package.json", json)
            write("pnpm-workspace.yaml")
            assertNull(detector.detect(repo))
        }
    }

    @Test fun `malformed child manifest does not hide a declaration above it`() {
        declare()
        write("src/package.json", "{broken json")
        assertEquals(repo, detector.detect(write("src/index.ts"))?.root)
    }

    @Test fun `force mode uses nearest package with stable cwd and respects fallback`() {
        write("package.json", "{}")
        val a = write("src/first/a.ts")
        val b = write("src/second/b.ts")
        assertEquals(repo, detector.detect(a, force = true, fallbackRoot = repo)?.root)
        assertEquals(repo, detector.detect(b, force = true, fallbackRoot = repo)?.root)
        Files.delete(repo.resolve("package.json"))
        assertEquals(repo, detector.detect(a, force = true, fallbackRoot = repo)?.root)
    }

    @Test fun `new and removed installs are detected without stale results`() {
        declare()
        assertNull(detector.detect(repo)?.vpPath)
        val vp = install()
        assertEquals(vp, detector.detect(repo)?.vpPath)
        Files.delete(vp)
        assertNull(detector.detect(repo)?.vpPath)
    }

    @Test fun `package entry requires vite-plus identity and a real file`() {
        declare()
        write("node_modules/vite-plus/package.json", """{"name":"unrelated","bin":{"vp":"bin/vp"}}""")
        write("node_modules/vite-plus/bin/vp")
        assertNull(detector.detect(repo)?.vpPath)
        write("node_modules/vite-plus/package.json", """{"name":"vite-plus","bin":"dist/cli.js"}""")
        val entry = write("node_modules/vite-plus/dist/cli.js", "#!/usr/bin/env node\n")
        assertEquals(entry, detector.detect(repo)?.vpPath)
    }

    @Test fun `Windows uses cmd or Bun exe shims and skips POSIX shims`() {
        val windows = VitePlusDetector(windows = true)
        declare()
        write("node_modules/.bin/vp")
        assertNull(windows.detect(repo)?.vpPath)
        val exe = write("node_modules/.bin/vp.exe")
        assertEquals(exe, windows.detect(repo)?.vpPath)
        val cmd = write("node_modules/.bin/vp.cmd")
        assertEquals(cmd, windows.detect(repo)?.vpPath)
    }

    @Test fun `a vp directory is not an executable`() {
        declare()
        Files.createDirectories(repo.resolve("node_modules/.bin/vp"))
        assertNull(detector.detect(repo)?.vpPath)
    }
}
