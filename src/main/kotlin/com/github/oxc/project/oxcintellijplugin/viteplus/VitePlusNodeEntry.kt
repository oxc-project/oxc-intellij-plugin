package com.github.oxc.project.oxcintellijplugin.viteplus

import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Read npm/pnpm shim targets without executing shell code or treating native vp binaries as JavaScript. */
object VitePlusNodeEntry {
    private val nodeShebang = Regex("""^#!\s*(?:\S*/node|\S*/env\s+(?:-S\s+)?node)(?:\s|$)""")
    private val shellShebang = Regex("""^#!.*(?:/|\s)(?:sh|bash|dash|zsh|ksh)(?:\s|$)""")
    private val cmdTarget = Regex(""""((?:%(?:~dp0|dp0%)[\\/]|[a-zA-Z]:[\\/]|\\\\)[^"\r\n]+)"[ \t]+%\*""")
    private val shellTarget = Regex(""""\${'$'}basedir/([^"\r\n]+)"[ \t]+"\${'$'}@"""")
    private val cmdPrefix = Regex("""^%(?:~dp0|dp0%)[\\/]""")

    fun isNodeScript(path: Path): Boolean = try {
        nodeShebang.containsMatchIn(header(path))
    } catch (_: IOException) {
        false
    }

    fun resolve(path: Path): Path? = try {
        resolveEntry(path)
    } catch (_: IOException) {
        null
    } catch (_: InvalidPathException) {
        null
    }

    private fun resolveEntry(path: Path): Path? {
        val header = header(path)
        if (nodeShebang.containsMatchIn(header)) return path
        val isCmd = path.fileName.toString().endsWith(".cmd", ignoreCase = true)
        if (!isCmd && !shellShebang.containsMatchIn(header)) return null
        val shim = Files.readString(path)
        val target = (if (isCmd) cmdTarget else shellTarget).find(shim)?.groupValues?.get(1)
        val shimPath = if (!isCmd && Files.isSymbolicLink(path)) path.toRealPath() else path
        val binDir = shimPath.toAbsolutePath().parent
        val entry = when {
            target != null -> binDir.resolve(if (isCmd) {
                cmdPrefix.replace(target, "").replace('\\', java.io.File.separatorChar)
            } else target).normalize()
            binDir.fileName.toString() == ".bin" -> binDir.resolve("../vite-plus/bin/vp").normalize()
            isCmd -> binDir.resolve("node_modules/vite-plus/bin/vp")
            else -> return null
        }
        return entry.takeIf { isNodeScript(it) }
    }

    private fun header(path: Path): String = Files.newInputStream(path).use {
        it.readNBytes(256).toString(Charsets.UTF_8).lineSequence().first()
    }
}
