package com.github.oxc.project.oxcintellijplugin.viteplus

import com.github.oxc.project.oxcintellijplugin.BinarySource
import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintPackage
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import org.eclipse.lsp4j.ConfigurationItem

class VitePlusIntegrationTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {
    private val lint get() = OxlintSettings.getInstance(project)
    private val fmt get() = OxfmtSettings.getInstance(project)

    private fun add(path: String, text: String = "") = myFixture.addFileToProject(path, text).virtualFile

    private fun install() {
        add("node_modules/vite-plus/package.json", """{"name":"vite-plus","version":"0.3.1","bin":{"vp":"bin/vp"}}""")
        add("node_modules/vite-plus/bin/vp", "#!/bin/sh\n")
        for (tool in listOf("oxlint", "oxfmt")) {
            add("node_modules/$tool/package.json", """{"name":"$tool","version":"1.82.0","bin":{"$tool":"bin/$tool"}}""")
            add("node_modules/$tool/bin/$tool", "#!/bin/sh\n")
        }
    }

    fun testOneToolCannotDismissTheOtherToolsError() {
        val notifications = VitePlusNotifications.getInstance(project)
        notifications.launchFailed("/fixture", "Oxlint")
        PlatformTestUtil.waitWithEventsDispatching("Missing startup failure notification", {
            NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
                .any { it.content.contains("Oxlint") }
        }, 5)
        val failure = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
            .first { it.content.contains("Oxlint") }
        notifications.started("/fixture", "Oxfmt")
        assertFalse(failure.isExpired)
        notifications.started("/fixture", "Oxlint")
        assertTrue(failure.isExpired)

        notifications.unavailable("/fixture", "missing-fmt")
        PlatformTestUtil.waitWithEventsDispatching("Missing install notification", {
            NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
                .any { it.content.contains("missing-fmt") }
        }, 5)
        val missing = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
            .first { it.content.contains("missing-fmt") }
        notifications.resolved("/fixture", "")
        assertFalse(missing.isExpired)
        notifications.resolved("/fixture", "missing-fmt")
        assertTrue(missing.isExpired)
    }

    fun testIndependentSourcesAndLaunchArguments() {
        add("package.json", """{"devDependencies":{"vite-plus":"*","oxlint":"*","oxfmt":"*"}}""")
        install()
        val file = add("src/index.js", "debugger;")
        for (lintSource in BinarySource.entries) {
            for (fmtSource in BinarySource.entries) {
                lint.binarySource = lintSource
                fmt.binarySource = fmtSource
                val lintCommand = OxlintPackage(project).resolveCommand(file)!!
                val fmtCommand = OxfmtPackage(project).resolveCommand(file)!!
                assertEquals(lintSource != BinarySource.OXC, lintCommand.vitePlus)
                assertEquals(fmtSource != BinarySource.OXC, fmtCommand.vitePlus)
                assertEquals(if (lintCommand.vitePlus) listOf("lint", "--lsp") else listOf("--lsp"), lintCommand.arguments)
                assertEquals(if (fmtCommand.vitePlus) listOf("fmt", "--lsp") else listOf("--lsp"), fmtCommand.arguments)
                assertEquals(file.parent.parent, lintCommand.root)
                assertEquals(file.parent.parent, fmtCommand.root)
                assertTrue(lintCommand.executable.endsWith(if (lintCommand.vitePlus) "/vite-plus/bin/vp" else "/oxlint/bin/oxlint"))
                assertTrue(fmtCommand.executable.endsWith(if (fmtCommand.vitePlus) "/vite-plus/bin/vp" else "/oxfmt/bin/oxfmt"))
            }
        }
    }

    fun testManualPathsKeepPriorityAndDisabledToolsDoNotResolve() {
        add("package.json", """{"devDependencies":{"vite-plus":"*"}}""")
        install()
        val file = add("index.js")
        for (source in BinarySource.entries) {
            lint.binarySource = source
            fmt.binarySource = source
            lint.vitePlusPath = "missing-vp"
            fmt.vitePlusPath = "missing-vp"
            lint.configurationMode = ConfigurationMode.MANUAL
            fmt.configurationMode = ConfigurationMode.MANUAL
            lint.binaryPath = add("custom-lint", "#!/bin/sh\n").path
            fmt.binaryPath = add("custom-fmt", "#!/bin/sh\n").path
            lint.binaryParameters = mutableListOf("--custom")
            val lintCommand = OxlintPackage(project).resolveCommand(file)!!
            val fmtCommand = OxfmtPackage(project).resolveCommand(file)!!
            assertEquals(lint.binaryPath, lintCommand.executable)
            assertEquals(listOf("--custom"), lintCommand.arguments)
            assertEquals(fmt.binaryPath, fmtCommand.executable)
            assertFalse(lintCommand.vitePlus)
            assertFalse(fmtCommand.vitePlus)
        }
        lint.configurationMode = ConfigurationMode.DISABLED
        fmt.configurationMode = ConfigurationMode.DISABLED
        assertNull(OxlintPackage(project).resolveCommand(file))
        assertNull(OxfmtPackage(project).resolveCommand(file))
    }

    fun testExplicitVpAndForceModeWorkWithoutDeclaration() {
        add("package.json", "{}")
        install()
        val file = add("src/index.js")
        val explicit = add("tools/vp", "#!/bin/sh\n")
        lint.vitePlusPath = "tools/vp"
        fmt.vitePlusPath = explicit.path
        assertEquals(explicit.path, OxlintPackage(project).resolveCommand(file)!!.executable)
        assertEquals(explicit.path, OxfmtPackage(project).resolveCommand(file)!!.executable)
        lint.binarySource = BinarySource.OXC
        fmt.binarySource = BinarySource.OXC
        assertFalse(OxlintPackage(project).resolveCommand(file)!!.vitePlus)
        assertFalse(OxfmtPackage(project).resolveCommand(file)!!.vitePlus)
        lint.vitePlusPath = ""
        fmt.vitePlusPath = ""
        lint.binarySource = BinarySource.VITE_PLUS
        fmt.binarySource = BinarySource.VITE_PLUS
        assertTrue(OxlintPackage(project).resolveCommand(file)!!.vitePlus)
        assertTrue(OxfmtPackage(project).resolveCommand(file)!!.vitePlus)
    }

    fun testUnavailableExplicitVpDoesNotFallBackAndCanRecover() {
        add("package.json", """{"devDependencies":{"vite-plus":"*"}}""")
        install()
        val file = add("index.js")
        lint.vitePlusPath = "missing-vp"
        fmt.vitePlusPath = "missing-vp"
        assertNull(OxlintPackage(project).resolveCommand(file))
        assertNull(OxfmtPackage(project).resolveCommand(file))
        add("missing-vp", "#!/bin/sh\n")
        assertTrue(OxlintPackage(project).resolveCommand(file)!!.vitePlus)
        assertTrue(OxfmtPackage(project).resolveCommand(file)!!.vitePlus)
    }

    fun testNestedVitePackagesDoNotShareServersWithParentOrPlainSiblings() {
        add("package.json", """{"workspaces":["packages/*"],"devDependencies":{"oxlint":"*","oxfmt":"*"}}""")
        install()
        add("packages/app/package.json", """{"devDependencies":{"vite-plus":"*"}}""")
        add("packages/app/nested/package.json", """{"devDependencies":{"vite-plus":"*"}}""")
        val app = add("packages/app/src/index.js")
        val nested = add("packages/app/nested/index.js")
        val plain = add("packages/plain/index.js")
        val appCommand = OxlintPackage(project).resolveCommand(app)!!
        val nestedCommand = OxlintPackage(project).resolveCommand(nested)!!
        val plainCommand = OxlintPackage(project).resolveCommand(plain)!!
        assertEquals(app.parent.parent, appCommand.root)
        assertEquals(nested.parent, nestedCommand.root)
        assertTrue(appCommand.supports(app, project, BinarySource.AUTO, "", false))
        assertFalse(appCommand.supports(nested, project, BinarySource.AUTO, "", false))
        assertFalse(appCommand.supports(plain, project, BinarySource.AUTO, "", false))
        assertFalse(plainCommand.supports(app, project, BinarySource.AUTO, "", false))
        assertTrue(plainCommand.supports(plain, project, BinarySource.AUTO, "", false))
    }

    fun testViteNestedConfigOverridesAtInitializationAndConfigurationRequests() {
        add("package.json", """{"devDependencies":{"vite-plus":"*"}}""")
        install()
        val file = add("index.js")
        lint.disableNestedConfig = false
        fmt.disableNestedConfig = false
        val lintCommand = OxlintPackage(project).resolveCommand(file)!!
        val fmtCommand = OxfmtPackage(project).resolveCommand(file)!!
        val lintDescriptor = OxlintLspServerDescriptor(project, lintCommand)
        val fmtDescriptor = OxfmtLspServerDescriptor(project, fmtCommand)
        for ((descriptor, key) in listOf(lintDescriptor to "disableNestedConfig", fmtDescriptor to "fmt.disableNestedConfig")) {
            val options = descriptor.createInitializationOptions() as List<*>
            assertEquals(true, ((options.single() as Map<*, *>)["options"] as Map<*, *>)[key])
            for (suffix in listOf("", "/")) {
                val item = ConfigurationItem().apply { scopeUri = file.parent.toNioPath().toUri().toString().removeSuffix("/") + suffix }
                assertEquals(true, (descriptor.getWorkspaceConfiguration(item) as Map<*, *>)[key])
            }
        }
        assertFalse(lint.disableNestedConfig)
        assertFalse(fmt.disableNestedConfig)
        lint.configurationMode = ConfigurationMode.MANUAL
        fmt.configurationMode = ConfigurationMode.MANUAL
        lint.binaryPath = add("standalone-lint", "#!/bin/sh\n").path
        fmt.binaryPath = add("standalone-fmt", "#!/bin/sh\n").path
        val standaloneLint = OxlintLspServerDescriptor(project, OxlintPackage(project).resolveCommand(file)!!)
        val standaloneFmt = OxfmtLspServerDescriptor(project, OxfmtPackage(project).resolveCommand(file)!!)
        val item = ConfigurationItem().apply { scopeUri = file.parent.toNioPath().toUri().toString() }
        assertEquals(false, (standaloneLint.getWorkspaceConfiguration(item) as Map<*, *>)["disableNestedConfig"])
        assertEquals(false, (standaloneFmt.getWorkspaceConfiguration(item) as Map<*, *>)["fmt.disableNestedConfig"])
        lint.disableNestedConfig = true
        fmt.disableNestedConfig = true
        assertEquals(true, (standaloneLint.getWorkspaceConfiguration(item) as Map<*, *>)["disableNestedConfig"])
        assertEquals(true, (standaloneFmt.getWorkspaceConfiguration(item) as Map<*, *>)["fmt.disableNestedConfig"])
    }
}
