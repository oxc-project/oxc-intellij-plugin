package com.github.oxc.project.oxcintellijplugin.viteplus

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** The declaring package is distinct from both the IDE content root and the hoisted install. */
data class VitePlusProject(val root: Path, val vpPath: Path?)

/** Ports https://github.com/voidzero-dev/vite-plus/pull/1614. No processes or project code run here. */
class VitePlusDetector(private val windows: Boolean = System.getProperty("os.name").startsWith("Windows")) {
    fun projectRoot(start: Path, force: Boolean = false, fallbackRoot: Path? = null): Path? {
        var dir = start.toAbsolutePath().normalize().let { if (Files.isRegularFile(it)) it.parent else it }
        while (true) {
            val pkg = readPackageJson(dir)
            if (force) {
                if (pkg != null || isWorkspaceRoot(dir, pkg)) return dir
                if (dir == fallbackRoot || dir.parent == null) return fallbackRoot ?: dir
            } else {
                if (declaresVitePlus(pkg)) return dir
                if (isWorkspaceRoot(dir, pkg) || dir.parent == null) return null
            }
            dir = dir.parent
        }
    }

    fun detect(
        start: Path,
        force: Boolean = false,
        fallbackRoot: Path? = null,
        globalVp: () -> Path? = { null },
    ): VitePlusProject? {
        val root = projectRoot(start, force, fallbackRoot) ?: return null
        var dir = root
        while (true) {
            // IntelliJ supplies the Node interpreter. Prefer the package entry to npm/pnpm shell shims.
            val vp = packageEntry(dir.resolve("node_modules/vite-plus"))
                ?: commandIn(dir.resolve("node_modules/.bin"))
            if (vp != null) return VitePlusProject(root, vp)
            if (isWorkspaceRoot(dir, readPackageJson(dir)) || dir.parent == null) break
            dir = dir.parent
        }
        return VitePlusProject(root, globalVp())
    }

    fun commandIn(directory: Path): Path? =
        (if (windows) listOf("vp.cmd", "vp.exe") else listOf("vp"))
            .map { directory.resolve(it) }.firstOrNull { Files.isRegularFile(it) }

    fun packageEntry(directory: Path): Path? {
        val pkg = readPackageJson(directory) ?: return null
        if (pkg.get("name")?.let { it.isJsonPrimitive && it.asString == "vite-plus" } != true) return null
        val bin = pkg.get("bin")
        val entry = when {
            bin?.isJsonPrimitive == true && bin.asJsonPrimitive.isString -> bin.asString
            bin?.isJsonObject == true -> bin.asJsonObject.get("vp")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            else -> null
        } ?: "bin/vp"
        val path = try {
            directory.resolve(entry).normalize()
        } catch (_: InvalidPathException) {
            return null
        }
        return path.takeIf { it.startsWith(directory.normalize()) && Files.isRegularFile(it) }
    }

    private fun declaresVitePlus(pkg: JsonObject?): Boolean =
        listOf("dependencies", "devDependencies").any { field ->
            pkg?.get(field)?.takeIf { it.isJsonObject }?.asJsonObject?.get("vite-plus")
                ?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotEmpty() } == true
        }

    private fun isWorkspaceRoot(dir: Path, pkg: JsonObject?): Boolean =
        Files.exists(dir.resolve("pnpm-workspace.yaml")) || Files.exists(dir.resolve("lerna.json")) ||
            pkg?.get("workspaces")?.isJsonNull == false

    private fun readPackageJson(dir: Path): JsonObject? = try {
        Files.newBufferedReader(dir.resolve("package.json")).use {
            JsonParser.parseReader(it).takeIf { json -> json.isJsonObject }?.asJsonObject
        }
    } catch (_: IOException) {
        null
    } catch (_: JsonParseException) {
        null
    }
}
