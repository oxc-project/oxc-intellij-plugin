package com.github.oxc.project.oxcintellijplugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.io.BaseOutputReader
import kotlin.io.path.Path


fun wrapStartProcess(processCreator: () -> OSProcessHandler): OSProcessHandler =
    ProgressManager.getInstance().runProcess(Computable(processCreator), EmptyProgressIndicator())

sealed interface OxcTargetRun {
    fun startProcess(): OSProcessHandler
    fun toTargetPath(path: String): String
    fun toLocalPath(path: String): String

    class Node(private val run: NodeTargetRun) : OxcTargetRun {
        override fun startProcess(): OSProcessHandler =
            wrapStartProcess {
                run.startProcessEx().processHandler
            }

        override fun toTargetPath(path: String) = run.convertLocalPathToTargetPath(path)
        override fun toLocalPath(path: String) = run.convertTargetPathToLocalPath(path)
    }

    class General(
        private val command: GeneralCommandLine,
        private val wslDistribution: WSLDistribution? = null,
    ) : OxcTargetRun {
        override fun startProcess(): OSProcessHandler =
            wrapStartProcess {
                object : CapturingProcessHandler(command) {
                    override fun readerOptions(): BaseOutputReader.Options {
                        return object : BaseOutputReader.Options() {
                            override fun splitToLines(): Boolean = false
                        }
                    }
                }
            }

        override fun toTargetPath(path: String) = wslDistribution?.getWslPath(Path(path)) ?: path
        override fun toLocalPath(path: String) = wslDistribution?.getWindowsPath(path) ?: path
    }
}

class OxcTargetRunBuilder(val project: Project) {
    fun getBuilder(
        executable: String,
    ): ProcessCommandBuilder {
        if (executable.isEmpty()) {
            throw ExecutionException(OxcBundle.message("oxc.language.server.not.found"))
        }

        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
        if (interpreter !is NodeJsLocalInterpreter && interpreter !is WslNodeInterpreter) {
            throw ExecutionException(JavaScriptBundle.message("lsp.interpreter.error"))
        }
        val builder = NodeProcessCommandBuilder(project, interpreter)

        return builder.setExecutable(executable).setCharset(Charsets.UTF_8)
    }
}
