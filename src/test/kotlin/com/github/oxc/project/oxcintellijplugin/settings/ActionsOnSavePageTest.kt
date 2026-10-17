package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtOnSaveFixAllActionInfo
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintOnSaveFixAllActionInfo
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LoggedErrorProcessor
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * The plugin's entries on Settings | Tools | Actions on Save (`OxcOnSaveInfoProvider`). The page
 * mirrors each tool's "Run 'Fix All' on Save" checkbox and links to the tool's own settings page,
 * both of which are looked up by configurable id in the open Settings dialog.
 */
class ActionsOnSavePageTest : OxcLightTestCase() {

    private lateinit var dialog: TestSettingsDialog

    override fun setUp() {
        super.setUp()
        dialog = TestSettingsDialog(project)
    }

    override fun tearDown() {
        try {
            dialog.dispose()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    fun testEntriesForOxlintAndOxfmt() {
        assertEquals(listOf("Run Oxlint 'Fix All' on Save", "Run Oxfmt 'Fix All' on Save"),
            infos().map { it.actionOnSaveName })
    }

    fun testEntriesShowTheStoredSettingsBeforeThePagesAreOpened() {
        OxfmtSettings.getInstance(project).fixAllOnSave = true
        val (oxlint, oxfmt) = infos()

        assertFalse(oxlint.isActionOnSaveEnabled)
        assertTrue(oxlint.isSaveActionApplicable)
        assertEquals("Oxlint 'Fix All'", oxlint.comment?.commentText)
        assertTrue(oxfmt.isActionOnSaveEnabled)
        assertEquals("Oxfmt 'Fix All'", oxfmt.comment?.commentText)
    }

    fun testEntriesAreNotApplicableWhileTheToolIsDisabled() {
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED
        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED
        val (oxlint, oxfmt) = infos()

        assertFalse(oxlint.isSaveActionApplicable)
        assertEquals("Oxlint integration is disabled.", oxlint.comment?.commentText)
        assertFalse(oxfmt.isSaveActionApplicable)
        assertEquals("Oxfmt integration is disabled.", oxfmt.comment?.commentText)
    }

    fun testOxfmtCheckboxIsMirroredOnItsSettingsPageAndSaved() {
        val oxfmt = infos()[1]

        oxfmt.isActionOnSaveEnabled = true

        val page = dialog.initializedPage(OxfmtConfigurable.CONFIGURABLE_ID) as OxfmtConfigurable
        assertTrue(page.fixAllOnSaveCheckBox.isSelected)
        assertTrue(oxfmt.isActionOnSaveEnabled)
        assertFalse(OxfmtSettings.getInstance(project).fixAllOnSave)

        page.apply()
        assertTrue(OxfmtSettings.getInstance(project).fixAllOnSave)
    }

    fun testOxfmtEntryFollowsChangesOnItsSettingsPage() {
        // The page was opened before the Actions on Save page, so the entry reads its UI state.
        val page = dialog.initializedPage(OxfmtConfigurable.CONFIGURABLE_ID, create = true) as OxfmtConfigurable
        val oxfmt = infos()[1]

        page.disabledConfiguration.isSelected = true

        assertFalse(oxfmt.isSaveActionApplicable)
    }

    fun testOxfmtLinkOpensItsSettingsPage() {
        val link = infos()[1].actionLinks.single()

        link.doClick()

        assertInstanceOf(dialog.selected.single(), OxfmtConfigurable::class.java)
    }

    /**
     * QUESTIONABLE: the Oxlint entry looks its settings page up by `OxlintConfigurable.CONFIGURABLE_ID`,
     * which is not the id the page is registered under (see `PluginRegistrationTest`). Ticking the
     * checkbox on the Actions on Save page therefore logs an internal error and is lost; it never
     * reaches the Oxlint page, so it cannot be saved.
     */
    fun testOxlintCheckboxIsLostBecauseItsSettingsPageIsNotFound() {
        val oxlint = infos()[0]

        val errors = captureErrors { oxlint.isActionOnSaveEnabled = true }

        assertTrue(errors.toString(), errors.any { it.contains("Failed to initialize configurable with id=${OxlintConfigurable.CONFIGURABLE_ID}") })
        assertFalse(oxlint.isActionOnSaveEnabled)
        assertFalse(OxlintSettings.getInstance(project).fixAllOnSave)
    }

    /**
     * QUESTIONABLE: for the same reason, the Oxlint entry's settings link selects nothing instead
     * of opening the Oxlint page.
     */
    fun testOxlintLinkDoesNotOpenItsSettingsPage() {
        val link = infos()[0].actionLinks.single()

        captureErrors { link.doClick() }

        assertFalse(dialog.selected.toString(), dialog.selected.any { it is OxlintConfigurable })
    }

    /** The plugin's entries among everything the registered providers put on the page, in page order. */
    private fun infos(): List<ActionOnSaveInfo> {
        val all = ActionOnSaveInfoProvider::class.java
            .getDeclaredMethod("getAllActionOnSaveInfos", ActionOnSaveContext::class.java)
            .apply { isAccessible = true }
            .invoke(null, context()) as List<*>
        return all.filterIsInstance<ActionOnSaveInfo>()
            .filter { it is OxlintOnSaveFixAllActionInfo || it is OxfmtOnSaveFixAllActionInfo }
    }

    /** The context the Actions on Save page passes to its providers; its constructor is internal. */
    private fun context(): ActionOnSaveContext {
        val constructor = ActionOnSaveContext::class.java.getDeclaredConstructor(Project::class.java,
            Settings::class.java, Disposable::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(project, dialog, testRootDisposable)
    }

    private fun captureErrors(block: () -> Unit): List<String> {
        val errors = mutableListOf<String>()
        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>,
                t: Throwable?): Set<Action> {
                errors.add("$message ${t?.message ?: ""}")
                return emptySet()
            }
        }) { block() }
        return errors
    }

    /**
     * A minimal Settings dialog over the registered settings pages: it creates a page's UI when
     * asked to, like the real dialog does when a page is opened, and records selected pages.
     */
    private class TestSettingsDialog(project: Project) :
        Settings(listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(project, true))) {

        private val initialized = mutableListOf<Configurable>()
        val selected = mutableListOf<Configurable?>()

        fun initializedPage(id: String, create: Boolean = false): Configurable? {
            val page = getConfigurableWithInitializedUiComponent(id, create) ?: return null
            return (page as? ConfigurableWrapper)?.rawConfigurable as? Configurable ?: page
        }

        override fun selectImpl(configurable: Configurable?): Promise<in Any> {
            selected.add((configurable as? ConfigurableWrapper)?.rawConfigurable as? Configurable ?: configurable)
            return resolvedPromise()
        }

        override fun getConfigurableWithInitializedUiComponentImpl(configurable: Configurable,
            initializeUiComponentIfNotYet: Boolean): Configurable? {
            if (configurable !in initialized) {
                if (!initializeUiComponentIfNotYet) return null
                configurable.createComponent()
                configurable.reset()
                initialized.add(configurable)
            }
            return configurable
        }

        override fun checkModifiedImpl(configurable: Configurable) {}

        override fun setSearchText(option: String?) {}

        fun dispose() {
            initialized.forEach { it.disposeUIResources() }
        }
    }
}
