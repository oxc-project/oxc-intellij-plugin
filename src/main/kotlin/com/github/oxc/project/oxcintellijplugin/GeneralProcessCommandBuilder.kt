package com.github.oxc.project.oxcintellijplugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.getWslPathSafe
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.Charset
import kotlin.io.path.Path

class GeneralProcessCommandBuilder : ProcessCommandBuilder {
    private val command =
        GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    private var executable: String? = null
    private var workingDir: String? = null
    private var inputFile: VirtualFile? = null
    private var charset: Charset? = null
    private val parameters = mutableListOf<ProcessCommandParameter>()

    override fun setWorkingDirectory(path: String?): ProcessCommandBuilder {
        workingDir = path
        return this
    }

    override fun setInputFile(file: VirtualFile?): ProcessCommandBuilder {
        inputFile = file
        return this
    }

    override fun addParameters(params: List<ProcessCommandParameter>): ProcessCommandBuilder {
        parameters.addAll(params)
        return this
    }

    override fun setExecutable(executable: String): ProcessCommandBuilder {
        this.executable = executable
        return this
    }

    override fun setCharset(charset: Charset): ProcessCommandBuilder {
        this.charset = charset
        return this
    }

    override fun build(): OxcTargetRun {
        val exec = executable ?: throw ExecutionException(OxcBundle.message("oxlint.language.server.not.found"))

        command.withExePath(exec)
        workingDir?.let { command.withWorkingDirectory(Path(it)) }
        inputFile?.let { command.withInput(File(it.path)) }
        charset?.let { command.withCharset(it) }

        // patch the command line to be run on WSL 2 instead of the host
        val wslDistribution = WslPath.getDistributionByWindowsUncPath(exec)
        if (wslDistribution != null) {
            command.withExePath(wslDistribution.getWslPathSafe(Path(exec)))

            // add command parameters, converting file paths if needed
            command.addParameters(parameters.map {
                when (it) {
                    is ProcessCommandParameter.Value -> it.value
                    is ProcessCommandParameter.FilePath -> wslDistribution.getWslPathSafe(it.path)
                }
            })

            val options = WSLCommandLineOptions().apply {
                isPassEnvVarsUsingInterop = true

                workingDir?.let {
                    remoteWorkingDirectory = wslDistribution.getWslPathSafe(Path(it))
                }
            }

            wslDistribution.patchCommandLine(command, null, options)
        } else {
            command.addParameters(parameters.map { it.toString() })
        }

        return OxcTargetRun.General(command, wslDistribution)
    }
}
