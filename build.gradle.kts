import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.rpc) apply false
    alias(libs.plugins.changelog)
    alias(libs.plugins.kover)
    id("org.jetbrains.intellij.platform")
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

subprojects {
    apply(plugin = "org.jetbrains.intellij.platform.module")
    apply(plugin = "rpc")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    // The platform finds a content module's descriptor at lib/modules/<module name>.jar, so each jar
    // must be named after the module in plugin.xml, such as Oxc.Shared.jar for the shared project.
    tasks.withType<ComposedJarTask>().configureEach {
        archiveFileName = "Oxc.${project.name.replaceFirstChar(Char::uppercase)}.jar"
    }
}

allprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            @Suppress("UnstableApiUsage")
            jvmToolchain {
                languageVersion = JavaLanguageVersion.of(25)
                vendor = JvmVendorSpec.JETBRAINS
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit)

    // The tests live in this project but run the code of every module, so the coverage report
    // merges in each module's classes.
    kover(project(":shared"))
    kover(project(":frontend"))
    kover(project(":backend"))

    intellijPlatform {
        // Unreleased/EAP versions do not have an installer.
        // TODO: Update this to use an installer when configured for a release version
        //  and to not use the installer when configured for an EAP version.
        intellijIdea(properties("platformVersion")) {
            useInstaller = false
        }

        pluginModule(implementation(project(":shared")))
        pluginModule(implementation(project(":frontend")))
        pluginModule(implementation(project(":backend")))

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        testBundledPlugin("JavaScript")
    }
}

intellijPlatform {
    splitMode = true
    pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BOTH

    pluginConfiguration {
        version = properties("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md for the plugin's manifest.
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException(
                        "Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased()).withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        // A pre-release label such as 2.1.7-alpha.3 selects a Marketplace release channel.
        channels = properties("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaUltimate, properties("platformVersion"))
        }
        ignoredProblemsFile = layout.projectDirectory.file("plugin-verifier-ignored-problems.txt")
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeMonolith") {
            splitMode = false
        }
    }
}

kover {
    currentProject {
        instrumentation {
            // The platform's UI test task builds the plugin zip first, and there are no UI tests.
            disabledForTestTasks.add("testUi")
        }
    }

    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

tasks {
    publishPlugin {
        dependsOn(patchChangelog)
    }

    test {
        systemProperty(
            "idea.log.trace.categories",
            "#com.github.oxc.project.oxcintellijplugin,#com.intellij.platform.lsp",
        )

        // The test framework creates its projects here, so this can put them inside WSL.
        environment("OXC_TEST_TMPDIR").orNull?.takeIf { it.isNotBlank() }?.let {
            systemProperty("java.io.tmpdir", it)
            // IJent loads Netty while connecting to WSL, so Netty's temp folder can't be inside WSL.
            systemProperty(
                "io.grpc.netty.shaded.io.netty.tmpdir",
                layout.buildDirectory.dir("tmp/netty").get().asFile.absolutePath,
            )
        }

        // The tests read these themselves, so they must be inputs. Otherwise, a WSL job in CI can take
        // the cached result of the Windows job, which restores the same cache, and skip its tests.
        listOf("OXC_TEST_WSL_DISTRO", "OXC_TEST_WSL_NODE", "OXC_TEST_VITEPLUS_FIXTURE").forEach {
            inputs.property(it, environment(it).orElse(""))
        }
    }
}
