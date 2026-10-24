package com.github.oxc.project.oxcintellijplugin.integration

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.kodein.di.DI
import org.kodein.di.bindSingleton

/**
 * Runs WebStorm with the plugin installed, as a user would, on a project that has `oxlint` and
 * `oxfmt` installed in `node_modules` (fixture `testData/onsave`, whose `.oxlintrc.json` turns on
 * `prefer-const`). Unlike the tests under `src/test`, nothing inside the IDE is replaced for
 * testing: the servers are found, started and talked to exactly as for users.
 *
 * Subclasses decide where the project lives and which Node.js runs the servers.
 */
abstract class OxcIdeTest {

    /** Creates an empty directory for the project. */
    protected abstract fun createProjectDir(): Path

    /** Installs the project's dependencies, as the user would with npm. */
    protected abstract fun installDependencies(projectDir: Path)

    /** The project's Node.js interpreter, as `NodeJsInterpreterRef.create` takes it. */
    protected abstract val nodeInterpreter: String

    @Test
    fun lintsFixesAndFormatsAFile(testInfo: TestInfo) {
        val projectDir = createProjectDir()
        copyFixture("onsave", projectDir)
        installDependencies(projectDir)

        Starter.newContext(testInfo.testMethod.get().name, TestCase(IdeProductProvider.WS, LocalProjectInfo(projectDir))
            .withVersion(System.getProperty("oxc.test.ide.version")))
            .apply { PluginConfigurator(this).installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin"))) }
            .runIdeWithDriver()
            .useDriverAndCloseIde {
                waitForIndicators(5.minutes)
                val project = singleProject()
                // Before any file is opened, so the servers start with this interpreter.
                service<NodeJsInterpreterManager>(project)
                    .setInterpreterRef(utility<NodeJsInterpreterRef>().create(nodeInterpreter))

                // The SDK's own wait for code analysis is fixed at one minute; the longer wait for the
                // Oxlint highlight below covers it.
                openFile("index.js", project, waitForCodeAnalysis = false)
                val editor = ideFrame().codeEditor()
                val document = editor.document

                waitFor("Oxlint reports prefer-const", 2.minutes,
                    getter = { getHighlights(document, project).mapNotNull { it.getDescription() } },
                    checker = { descriptions -> descriptions.any { "prefer-const" in it } })

                invokeAction("OxlintApplyFixAllAction", component = editor.component)
                waitForText(document::getText, "const a = {b:1}\nconsole.log(a)\n")

                invokeAction("OxfmtApplyFixAllAction", component = editor.component)
                waitForText(document::getText, "const a = { b: 1 };\nconsole.log(a);\n")
            }
    }

    private fun Driver.waitForText(text: () -> String, expected: String) {
        waitFor("Editor text is ${expected.trim()}", 1.minutes, getter = text, checker = { it == expected })
    }

    private fun copyFixture(name: String, target: Path) {
        val source = Path.of(System.getProperty("oxc.test.data")).resolve(name)
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(path.relativeTo(source).toString())
                if (path.isDirectory()) destination.createDirectories() else path.copyTo(destination, overwrite = true)
            }
        }
    }

    protected fun run(vararg command: String, directory: Path? = null) {
        val process = ProcessBuilder(*command).apply { directory?.let { directory(it.toFile()) } }
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "${command.joinToString(" ")} failed:\n$output" }
    }

    companion object {
        init {
            // Exceptions logged inside the IDE fail the test instead of only being written to its log.
            di = DI {
                extend(di)
                bindSingleton<CIServer>(overrides = true) {
                    object : CIServer by NoCIServer {
                        override fun reportTestFailure(testName: String, message: String, details: String, linkToLogs: String?) {
                            fail<Unit>("$testName fails: $message.\n$details")
                        }
                    }
                }
            }
        }
    }
}
