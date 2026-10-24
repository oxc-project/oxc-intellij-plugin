package com.github.oxc.project.oxcintellijplugin.testutil

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.name

/**
 * Copies a fixture from `src/test/testData` into a test project and installs its dependencies with
 * the requested [PackageManager].
 *
 * Installs are done once per fixture, package manager and `package.json` contents into
 * `build/oxc-test-fixtures`, then linked (or copied) into each test project. Symlinks are recreated
 * as symlinks, which keeps pnpm's relative `node_modules/<pkg> -> .pnpm/...` layout intact.
 */
object TestFixtures {

    val testDataRoot: Path = Path.of("src", "test", "testData").absolute()
    private val cacheRoot: Path = Path.of("build", "oxc-test-fixtures").absolute()

    /** Source files that belong to the fixture but must not be copied from the source tree. */
    private val excludedSourceNames = setOf(
        "node_modules", ".idea", ".yarn", ".pnp.cjs", ".pnp.loader.mjs", ".yarnrc.yml",
        "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "bun.lock", "bun.lockb",
    )

    /** Copies the fixture's sources (no dependencies) into [target]. */
    fun copySources(fixture: String, target: Path) {
        val source = testDataRoot.resolve(fixture)
        check(source.isDirectory()) { "Missing fixture $source" }
        copyTree(source, target, link = false) { it.name !in excludedSourceNames }
    }

    /** Copies the fixture's sources into [target] and installs its dependencies there. */
    fun setUp(fixture: String, packageManager: PackageManager, target: Path) {
        copySources(fixture, target)
        val installed = installed(fixture, packageManager)
        copyTree(installed, target, link = true) { it.name != "package.json" && it.name != ".installed" }
    }

    /** Returns a directory holding the fixture's `package.json` with its dependencies installed. */
    @Synchronized
    fun installed(fixture: String, packageManager: PackageManager): Path {
        val tools = NodeTooling.get()
        val packageJson = Files.readString(testDataRoot.resolve(fixture).resolve("package.json"))
        val key = sha256("${packageManager.name}\n${tools.versionKey}\n$packageJson").take(12)
        val directory = cacheRoot.resolve(fixture.replace('/', '_')).resolve("${packageManager.name.lowercase()}-$key")
        val marker = directory.resolve(".installed")
        if (marker.exists()) {
            return directory
        }

        if (directory.exists()) {
            // A previous install was interrupted; start from a clean directory.
            directory.toFile().deleteRecursively()
        }
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("package.json"), packageJson)
        packageManager.prepareInstallDirectory(directory)
        ProcessRunner.run(packageManager.installCommand(tools), directory, tools.environment(),
            "install $fixture with $packageManager")
        Files.writeString(marker, "ok")
        return directory
    }

    private fun copyTree(source: Path, target: Path, link: Boolean, include: (Path) -> Boolean) {
        Files.createDirectories(target)
        val symlinks = mutableListOf<Pair<Path, Path>>()
        Files.list(source).use { children ->
            for (child in children.toList()) {
                if (!include(child)) continue
                copyEntry(child, target.resolve(child.name), link, symlinks)
            }
        }
        // Symlinks are created last: on Windows a symlink to a directory that does not exist yet
        // is created as a file symlink, which cannot be traversed. pnpm links packages to siblings
        // in node_modules/.pnpm that are often copied later (oxfmt@x/node_modules/tinypool ->
        // ../../tinypool@y/node_modules/tinypool).
        for ((linkPath, linkTarget) in symlinks) {
            Files.deleteIfExists(linkPath)
            Files.createSymbolicLink(linkPath, linkTarget)
        }
    }

    private fun copyEntry(source: Path, target: Path, link: Boolean, symlinks: MutableList<Pair<Path, Path>>) {
        when {
            source.isSymbolicLink() -> symlinks += target to Files.readSymbolicLink(source)

            // Windows junctions (pnpm's default for directories there) are not symbolic links to
            // Java and are copied as directories.
            source.isDirectory(LinkOption.NOFOLLOW_LINKS) -> {
                Files.createDirectories(target)
                Files.list(source).use { children ->
                    for (child in children.toList()) {
                        copyEntry(child, target.resolve(child.name), link, symlinks)
                    }
                }
            }

            else -> {
                Files.deleteIfExists(target)
                // Hard links make installing large dependency trees (e.g. Vite+) into every test
                // project cheap. Fall back to copying across file systems.
                val linked = link && runCatching { Files.createLink(target, source) }.isSuccess
                if (!linked) {
                    try {
                        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES,
                            LinkOption.NOFOLLOW_LINKS)
                    } catch (_: FileAlreadyExistsException) {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS)
                    }
                }
            }
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
