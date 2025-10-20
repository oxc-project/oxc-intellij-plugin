package com.github.oxc.project.oxcintellijplugin.extensions

import com.github.oxc.project.oxcintellijplugin.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.OxlintPackage
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Find the nearest file that satisfies the predicate.
 * If `root` is not null, stops finding at the specified directory.
 */
private fun VirtualFile.findNearestFile(
    predicate: (file: VirtualFile) -> Boolean,
    root: VirtualFile? = null,
): VirtualFile? {
    var cur = this.parent
    while (cur != null && VfsUtil.isUnder(cur, mutableSetOf(root))) {
        val f = cur.children.find(predicate)
        if (f != null) {
            return f
        }
        cur = cur.parent
    }
    return null
}

fun VirtualFile.isOxfmtConfigFile(): Boolean =
    OxfmtPackage.configValidExtensions.map { "${OxfmtPackage.CONFIG_NAME}.$it" }.contains(this.name)

fun VirtualFile.isOxlintConfigFile(): Boolean =
    OxlintPackage.configValidExtensions.map { "${OxlintPackage.CONFIG_NAME}.$it" }.contains(this.name)

fun VirtualFile.isPackageJsonFile(): Boolean = this.name == "package.json"

fun VirtualFile.findNearestOxlintConfig(root: VirtualFile? = null): VirtualFile? =
    this.findNearestFile({ f -> f.isOxlintConfigFile() }, root)

fun VirtualFile.findNearestPackageJson(root: VirtualFile? = null): VirtualFile? =
    this.findNearestFile({ f -> f.isPackageJsonFile() }, root)
