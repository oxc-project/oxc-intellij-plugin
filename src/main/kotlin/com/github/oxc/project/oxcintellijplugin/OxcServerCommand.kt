package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusPackage
import com.intellij.javascript.nodejs.library.yarn.pnp.YarnPnpNodePackage
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path

/** Resolve the executable, arguments, cwd and source together so they cannot come from different installs. */
data class OxcServerCommand(
    val executable: String,
    val arguments: List<String>,
    val root: VirtualFile,
    val vitePlus: Boolean = false,
) {
    fun supports(file: VirtualFile, project: Project, source: BinarySource, vpPath: String, manualBinary: Boolean): Boolean {
        if (!file.toNioPath().startsWith(root.toNioPath())) return false
        val viteRoot = if (manualBinary) null else VitePlusPackage(project).projectRoot(file, source, vpPath)
        return if (vitePlus) viteRoot == root.toNioPath() else viteRoot == null
    }

    companion object {
        fun findRoot(project: Project, file: VirtualFile, nodePackage: NodePackage?): VirtualFile? {
            return when (nodePackage) {
                null -> ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file)
                is YarnPnpNodePackage -> nodePackage.getPackageJson(project)?.parent
                else -> VirtualFileManager.getInstance().findFileByNioPath(Path.of(nodePackage.systemIndependentPath))
                    ?.parent?.parent
            }
        }
    }
}
