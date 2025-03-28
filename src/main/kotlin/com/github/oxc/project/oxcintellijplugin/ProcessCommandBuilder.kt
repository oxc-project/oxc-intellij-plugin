package com.github.oxc.project.oxcintellijplugin

import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.Charset

interface ProcessCommandBuilder {
    fun setWorkingDirectory(path: String?): ProcessCommandBuilder
    fun setInputFile(file: VirtualFile?): ProcessCommandBuilder
    fun addParameters(params: List<ProcessCommandParameter>): ProcessCommandBuilder
    fun setExecutable(executable: String): ProcessCommandBuilder
    fun setCharset(charset: Charset): ProcessCommandBuilder
    fun build(): OxcTargetRun
}
