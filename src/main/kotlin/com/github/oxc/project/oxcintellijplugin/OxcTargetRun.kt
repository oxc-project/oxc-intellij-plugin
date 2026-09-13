package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintBundle
import com.github.oxc.project.oxcintellijplugin.viteplus.VitePlusNodeEntry
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
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
                val logger = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin")
                val level = if (logger.isTraceEnabled) "TRACE" else if (logger.isDebugEnabled) "DEBUG" else "INFO"

                val additionalProperties = mutableMapOf<String, String>()
                if (!run.envData.envs.contains("OXC_LOG")) {
                    additionalProperties["OXC_LOG"] = level
                }
                if (!run.envData.envs.contains("RUST_LOG")) {
                    additionalProperties["RUST_LOG"] = level
                }
                run.envData = run.envData.with(additionalProperties)
                run.startProcessEx().processHandler
            }

        override fun toTargetPath(path: String) = runCatching { run.convertLocalPathToTargetPath(path) }.getOrDefault(path)
        override fun toLocalPath(path: String) = runCatching { run.convertTargetPathToLocalPath(path) }.getOrDefault(path)
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
        configMode: ConfigurationMode,
        executable: String,
        detectRuntime: Boolean = false,
    ): ProcessCommandBuilder {
        if (executable.isEmpty()) {
            throw ExecutionException(OxlintBundle.message("oxlint.language.server.not.found"))
        }

        val nodeEntry = if (detectRuntime) VitePlusNodeEntry.resolve(Path(executable)) else null
        val launchExecutable = nodeEntry?.toString() ?: executable
        val wslPath = WslPath.parseWindowsUncPath(launchExecutable)
        val isNodeJs = if (detectRuntime) {
            nodeEntry != null
        } else {
            wslPath != null || VitePlusNodeEntry.isNodeScript(Path(launchExecutable))
        }

        val builder: ProcessCommandBuilder = if ((configMode == ConfigurationMode.MANUAL || detectRuntime) && !isNodeJs) {
            GeneralProcessCommandBuilder()
        } else {
            val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter ?: throw ExecutionException(JavaScriptBundle.message("lsp.interpreter.error"));
            NodeProcessCommandBuilder(project, interpreter)
        }

        return builder.setExecutable(launchExecutable).setCharset(Charsets.UTF_8)
    }

    companion object {
        init {
            // Warm the `mntRoot` cache to avoid synchronous execution on EDT errors.
            // https://github.com/oxc-project/oxc-intellij-plugin/issues/433
            // This is a no-op on systems without WSL.
            ApplicationManager.getApplication().executeOnPooledThread {
                runCatching {
                    WslDistributionManager.getInstance().installedDistributions.forEach { wslDistribution ->
                        runCatching {
                            wslDistribution.mntRoot
                        }.onFailure { throwable ->
                            thisLogger().warn("Failed to warm mntRoot cache " + wslDistribution.id, throwable)
                        }
                    }
                }.onFailure { throwable ->
                    thisLogger().warn("Failed to access installed WSL distributions", throwable)
                }
            }
        }
    }
}
