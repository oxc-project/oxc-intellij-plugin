package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.actions.OxfmtRestartServerAction
import com.github.oxc.project.oxcintellijplugin.actions.OxlintRestartServerAction
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtSchemaProviderFactory
import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtFixAllAction
import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtImportCodeStyleAction
import com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle.OxfmtCodeStyleSettingsModifier
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerSupportProvider
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintSchemaProviderFactory
import com.github.oxc.project.oxcintellijplugin.oxlint.actions.OxlintFixAllAction
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.settings.OxcOnSaveInfoProvider
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener
import com.intellij.ide.IconProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

/** Verifies that everything declared in plugin.xml is registered with the IDE. */
class PluginRegistrationTest : OxcLightTestCase() {

    fun testActionsAreRegistered() {
        val manager = ActionManager.getInstance()

        assertInstanceOf(manager.getAction("OxlintApplyFixAllAction"), OxlintFixAllAction::class.java)
        assertInstanceOf(manager.getAction("OxfmtApplyFixAllAction"), OxfmtFixAllAction::class.java)
        assertInstanceOf(manager.getAction("OxlintRestartServerAction"), OxlintRestartServerAction::class.java)
        assertInstanceOf(manager.getAction("OxfmtRestartServerAction"), OxfmtRestartServerAction::class.java)
        assertInstanceOf(manager.getAction("OxfmtImportCodeStyleAction"), OxfmtImportCodeStyleAction::class.java)
    }

    fun testActionTexts() {
        val manager = ActionManager.getInstance()

        assertEquals("Apply Oxlint Quick Fixes", manager.getAction("OxlintApplyFixAllAction").templateText)
        assertEquals("Apply Oxfmt Quick Fixes", manager.getAction("OxfmtApplyFixAllAction").templateText)
        assertEquals("Restart Oxlint Server", manager.getAction("OxlintRestartServerAction").templateText)
        assertEquals("Restart Oxfmt Server", manager.getAction("OxfmtRestartServerAction").templateText)
        assertEquals("Import Oxfmt Code Style", manager.getAction("OxfmtImportCodeStyleAction").templateText)
    }

    fun testFixAndImportActionsAreInTheEditorAndProjectViewContextMenus() {
        val manager = ActionManager.getInstance()
        for (groupId in listOf(IdeActions.GROUP_EDITOR_POPUP, IdeActions.GROUP_PROJECT_VIEW_POPUP)) {
            val group = manager.getAction(groupId) as DefaultActionGroup
            val ids = group.getChildActionsOrStubs().mapNotNull { manager.getId(it) }
            assertContainsElements(ids, "OxlintApplyFixAllAction", "OxfmtApplyFixAllAction", "OxfmtImportCodeStyleAction")
        }
    }

    fun testLanguageServerProvidersAreRegistered() {
        val providers = LspServerSupportProvider.EP_NAME.extensionList.map { it::class.java }

        assertContainsElements(providers, OxfmtLspServerSupportProvider::class.java,
            com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerSupportProvider::class.java)
    }

    fun testSchemaProvidersAreRegistered() {
        val factories = JsonSchemaProviderFactory.EP_NAME.extensionList.map { it::class.java }

        assertContainsElements(factories, OxlintSchemaProviderFactory::class.java, OxfmtSchemaProviderFactory::class.java)
    }

    fun testIconProviderIsRegistered() {
        assertTrue(IconProvider.EXTENSION_POINT_NAME.extensionList.any { it is OxcIconProvider })
    }

    fun testCodeStyleModifierRunsBeforeEditorConfig() {
        val modifiers = CodeStyleSettingsModifier.EP_NAME.extensionList
        val oxfmt = modifiers.indexOfFirst { it is OxfmtCodeStyleSettingsModifier }
        val editorConfig = modifiers.indexOfFirst { it::class.java.name == "org.editorconfig.EditorConfigCodeStyleSettingsModifier" }

        assertTrue(oxfmt >= 0)
        if (editorConfig >= 0) {
            assertTrue("Oxfmt must override EditorConfig", oxfmt < editorConfig)
        }
    }

    fun testNotificationGroupIsRegistered() {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)

        assertNotNull(group)
        assertEquals(NotificationDisplayType.BALLOON, group.displayType)
    }

    fun testFixAllTimeoutRegistryKeyDefaultsToThirtySeconds() {
        assertEquals(30_000, Registry.intValue("oxc.lint.fix.all.timeout.ms"))
    }

    fun testActionsOnSaveRunOxlintFirstAndOxfmtLast() {
        val ids = ExtensionPointName<ActionsOnSaveFileDocumentManagerListener.ActionOnSave>("com.intellij.actionOnSave")
            .extensionList.map { it::class.java.simpleName }
        val oxlint = ids.indexOf("OxlintFixAllOnSaveAction")
        val oxfmt = ids.indexOf("OxfmtFixAllOnSaveAction")
        val reformat = ids.indexOf("FormatOnSaveAction")

        assertTrue(ids.toString(), oxlint >= 0 && oxfmt >= 0)
        assertTrue("Oxlint must run before Oxfmt: $ids", oxlint < oxfmt)
        if (reformat >= 0) {
            assertTrue("Oxlint must run before the IDE reformat: $ids", oxlint < reformat)
            assertTrue("Oxfmt must run after the IDE reformat: $ids", reformat < oxfmt)
        }
    }

    fun testActionsOnSaveInfoProviderIsRegistered() {
        val provider = ActionOnSaveInfoProvider.EP_NAME.extensionList.filterIsInstance<OxcOnSaveInfoProvider>().single()

        assertEquals(listOf("Run 'Fix All' with Oxlint on Save", "Run 'Fix All' with Oxfmt on Save"),
            provider.searchableOptions.toList())
    }

    fun testOxfmtSettingsPageIsRegisteredUnderItsConfigurableId() {
        assertNotNull(findConfigurable(OxfmtConfigurable.CONFIGURABLE_ID))
        assertNotNull(findConfigurable("OxlintConfigurable"))
    }

    /**
     * QUESTIONABLE: `OxlintConfigurable.CONFIGURABLE_ID` (used by the Actions on Save page entry
     * and its "Configure..." link) is not the id the settings page is registered under in
     * plugin.xml (`OxlintConfigurable`), so lookups by that id find nothing. Oxfmt uses matching
     * ids.
     */
    fun testOxlintConfigurableIdDoesNotMatchTheRegisteredSettingsPage() {
        assertEquals("com.github.oxc.project.oxcintellijplugin.settings.OxcSettingsConfigurable",
            OxlintConfigurable.CONFIGURABLE_ID)
        assertNull(findConfigurable(OxlintConfigurable.CONFIGURABLE_ID))
    }

    private fun findConfigurable(id: String): Configurable? =
        ConfigurableExtensionPointUtil.getConfigurables(project, true, false)
            .firstOrNull { (it as? com.intellij.openapi.options.SearchableConfigurable)?.id == id ||
                           (it as? com.intellij.openapi.options.ex.ConfigurableWrapper)?.id == id }
}
