package com.github.oxc.project.oxcintellijplugin.extensions

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusPackage
import com.intellij.openapi.vfs.VirtualFile

fun VirtualFile.isOxlintJsonConfigFile(): Boolean =
    OxlintPackage.configValidJsonExtensions.map { "${OxlintPackage.CONFIG_NAME}.$it" }.contains(this.name)

fun VirtualFile.isOxlintConfigFile(): Boolean =
    isOxlintJsonConfigFile() || this.name == OxlintPackage.CONFIG_TS_NAME

fun VirtualFile.isOxfmtJsonConfigFile(): Boolean =
    OxfmtPackage.CONFIG_VALID_JSON_EXTENSIONS.map { "${OxfmtPackage.CONFIG_NAME}.$it" }.contains(this.name)

fun VirtualFile.isOxfmtConfigFile(): Boolean =
    isOxfmtJsonConfigFile() || this.name == OxfmtPackage.CONFIG_TS_NAME

fun VirtualFile.isViteConfigFile(): Boolean =
    VitePlusPackage.configValidExtensions.map { "${VitePlusPackage.CONFIG_NAME}.$it" }.contains(this.name)
