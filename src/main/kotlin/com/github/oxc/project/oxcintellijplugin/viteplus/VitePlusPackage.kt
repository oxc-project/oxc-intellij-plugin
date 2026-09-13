package com.github.oxc.project.oxcintellijplugin.viteplus

import com.github.oxc.project.oxcintellijplugin.BinarySource
import com.intellij.execution.wsl.WslPath
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.util.NodePackageDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EnvironmentUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

class VitePlusPackage(private val project: Project) {
    private fun detector(file: VirtualFile): VitePlusDetector =
        VitePlusDetector(com.intellij.openapi.util.SystemInfo.isWindows && WslPath.parseWindowsUncPath(file.path) == null)

    fun projectRoot(file: VirtualFile, source: BinarySource, vpPath: String): Path? {
        if (source == BinarySource.OXC) return null
        return detector(file).projectRoot(file.toNioPath(), source == BinarySource.VITE_PLUS || vpPath.isNotBlank(),
            contentRoot(file))
    }

    fun detect(file: VirtualFile, source: BinarySource, vpPath: String): VitePlusProject? {
        if (source == BinarySource.OXC) return null
        val detector = detector(file)
        val force = source == BinarySource.VITE_PLUS || vpPath.isNotBlank()
        if (vpPath.isNotBlank()) {
            val root = detector.projectRoot(file.toNioPath(), force, contentRoot(file)) ?: return null
            val configured = try {
                Path(vpPath).let { if (it.isAbsolute) it else (contentRoot(file) ?: root).resolve(it) }.normalize()
            } catch (_: InvalidPathException) {
                return VitePlusProject(root, null)
            }
            val executable = if (com.intellij.openapi.util.SystemInfo.isWindows && configured.extensionless()) {
                listOf(Path("$configured.cmd"), Path("$configured.exe"), configured).firstOrNull { Files.isRegularFile(it) }
            } else configured.takeIf { Files.isRegularFile(it) }
            return VitePlusProject(root, executable)
        }
        return detector.detect(file.toNioPath(), force, contentRoot(file)) { globalVp(detector, file) }
    }

    private fun contentRoot(file: VirtualFile): Path? =
        ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file)?.toNioPath()
            ?: project.basePath?.let { Path(it) }

    private fun globalVp(detector: VitePlusDetector, file: VirtualFile): Path? {
        // For WSL projects, use interpreter package locations instead of the host PATH.
        if (WslPath.parseWindowsUncPath(file.path) == null) {
            val envPath = EnvironmentUtil.getEnvironmentMap().entries.firstOrNull { it.key.equals("PATH", true) }?.value
            envPath?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.forEach { directory ->
                val path = try { Path(directory) } catch (_: InvalidPathException) { return@forEach }
                detector.commandIn(path.toAbsolutePath())?.let { return it }
            }
        }
        // findDefaultPackage also searches other project packages, which could cross the monorepo boundary.
        val packages = NodePackageDescriptor.findGloballyInstalledPackages(project, "vite-plus",
            NodeJsInterpreterManager.getInstance(project).interpreter)
        packages.firstNotNullOfOrNull { detector.packageEntry(it) }?.let { return it }
        if (WslPath.parseWindowsUncPath(file.path) == null) {
            return detector.packageEntry(Path(System.getProperty("user.home"), ".bun/install/global/node_modules/vite-plus"))
        }
        return null
    }

    private fun Path.extensionless(): Boolean = !fileName.toString().contains('.')

    companion object {
        const val CONFIG_NAME = "vite.config"
        val configValidExtensions = listOf("ts", "mts", "cts", "js", "mjs", "cjs")
    }
}
