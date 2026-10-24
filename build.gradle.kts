import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }
}

// Integration tests run a real IDE with the plugin installed (Starter + Driver framework), see
// https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val integrationTestImplementation by configurations.getting
val integrationTestRuntimeOnly by configurations.getting

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(libs.junit)

    // The plugin itself uses the IDE's Kotlin stdlib (kotlin.stdlib.default.dependency=false), but
    // the integration tests run outside the IDE, and Starter's kotlin-reflect needs a matching one.
    integrationTestImplementation(kotlin("stdlib"))
    integrationTestImplementation(platform(libs.junit5.bom))
    integrationTestImplementation(libs.junit5.jupiter)
    integrationTestImplementation(libs.kodein)
    integrationTestImplementation(libs.coroutines)
    integrationTestRuntimeOnly(libs.junit5.launcher)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(properties("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(properties("platformPlugins").map { it.split(',') })

        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = integrationTestImplementation.name)
    }
}

kotlin {
    @Suppress("UnstableApiUsage")
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = properties("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
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
        // Get the latest available change notes from the changelog file
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
            untilBuild = properties("pluginUntilBuild").orNull
        }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        // Custom IDEs to avoid disk space issues as the number of supported IDE versions grow.
        ides {
            // Targets the version specified by pluginSinceBuild as the earliest.
            select {
                sinceBuild = properties("pluginSinceBuild")
                untilBuild = properties("pluginSinceBuild").map { value -> "$value.*" }
            }

            // Targets the version specified by pluginUntilBuild as the latest. If pluginUntilBuild is
            // not provided, then the plugin verifier will automatically use the latest available version.
            latest {
                untilBuild = properties("pluginUntilBuild").orNull
            }
        }
        ignoredProblemsFile.set(File("plugin-verifier-ignored-problems.txt"))
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    currentProject {
        instrumentation {
            // Kover collects coverage from every test task, and its report runs on check. The
            // integration tests run a whole IDE and the plugin in a separate process, so they add
            // no coverage here; leaving them out also keeps them out of check. Run them with
            // `./gradlew integrationTest`.
            disabledForTestTasks.add("integrationTest")
        }
    }
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    publishPlugin {
        dependsOn(patchChangelog)
    }

    runIde {
        systemProperty("idea.log.trace.categories",
            "#com.github.oxc.project.oxcintellijplugin,#com.intellij.platform.lsp")
    }

    test {
        // On Windows the IDE reaches WSL files and processes through its IJent agent, which in
        // tests needs a test-only service implementation that is not shipped with the IDE. When
        // the WSL tests are enabled, fall back to plain \\wsl$ file access and wsl.exe.
        if (providers.environmentVariable("OXC_TEST_WSL_DISTRO").isPresent) {
            systemProperty("wsl.use.remote.agent.for.nio.filesystem", "false")
            systemProperty("wsl.use.remote.agent.for.launch.processes", "false")
        }
    }
}

intellijPlatformTesting {
    val integrationTest by testIdeUi.registering {
        task {
            val integrationTestSourceSet = sourceSets.getByName("integrationTest")
            testClassesDirs = integrationTestSourceSet.output.classesDirs
            classpath = integrationTestSourceSet.runtimeClasspath
            useJUnitPlatform()
            systemProperty("oxc.test.data", layout.projectDirectory.dir("src/test/testData").asFile.path)
            // Without a version, Starter downloads the newest WebStorm, EAPs included, which the
            // plugin may not support (pluginUntilBuild). Test with the IDE the plugin is built against.
            systemProperty("oxc.test.ide.version", properties("platformVersion").get())
            // Never alongside the unit tests: both start language servers and an IDE, and on CI
            // runners sharing the CPU makes both time out.
            mustRunAfter(tasks.test)
        }
    }
}
