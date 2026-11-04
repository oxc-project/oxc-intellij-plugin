package com.github.oxc.project.oxcintellijplugin.actions

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtFixAllAction
import com.github.oxc.project.oxcintellijplugin.oxfmt.actions.OxfmtImportCodeStyleAction
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.actions.OxlintFixAllAction
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.VirtualFile

/** When the plugin's actions are shown and enabled. Running them is covered by LSP tests. */
class ActionPresentationTest : OxcLightTestCase() {

    fun testOxlintFixAllIsEnabledForSupportedFiles() {
        assertPresentation(OxlintFixAllAction(), file("index.ts"), enabled = true, visibleInPopup = true)
    }

    fun testOxlintFixAllIsHiddenInPopupsForUnsupportedFiles() {
        assertPresentation(OxlintFixAllAction(), file("styles.css"), enabled = false, visibleInPopup = false)
    }

    fun testOxlintFixAllIsDisabledWhenOxlintIsDisabled() {
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED

        assertPresentation(OxlintFixAllAction(), file("index.ts"), enabled = false, visibleInPopup = false)
    }

    fun testOxfmtFixAllIsEnabledForSupportedFiles() {
        assertPresentation(OxfmtFixAllAction(), file("styles.css"), enabled = true, visibleInPopup = true)
    }

    fun testOxfmtFixAllIsHiddenInPopupsForUnsupportedFiles() {
        assertPresentation(OxfmtFixAllAction(), file("main.rs"), enabled = false, visibleInPopup = false)
    }

    fun testOxfmtFixAllIsDisabledWhenOxfmtIsDisabled() {
        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.DISABLED

        assertPresentation(OxfmtFixAllAction(), file("index.ts"), enabled = false, visibleInPopup = false)
    }

    fun testFixAllActionsAreDisabledWithoutAFile() {
        for (action in listOf(OxlintFixAllAction(), OxfmtFixAllAction())) {
            val event = event(action, null, ActionPlaces.KEYBOARD_SHORTCUT)
            action.update(event)
            assertFalse(event.presentation.isEnabled)
        }
    }

    fun testRestartActionsAreEnabledWithAProject() {
        for (action in listOf(OxlintRestartServerAction(), OxfmtRestartServerAction())) {
            val event = event(action, null, ActionPlaces.KEYBOARD_SHORTCUT)
            action.update(event)
            assertTrue(event.presentation.isEnabled)
        }
    }

    fun testImportCodeStyleIsVisibleWhenAnOxfmtConfigIsFound() {
        myFixture.addFileToProject(".oxfmtrc.json", "{}")
        val action = OxfmtImportCodeStyleAction()
        val event = event(action, file("src/index.js"), ActionPlaces.EDITOR_POPUP)

        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
    }

    fun testImportCodeStyleIsHiddenWithoutAnOxfmtConfig() {
        val action = OxfmtImportCodeStyleAction()
        val event = event(action, file("src/index.js"), ActionPlaces.EDITOR_POPUP)

        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    private fun assertPresentation(action: AnAction, file: VirtualFile, enabled: Boolean, visibleInPopup: Boolean) {
        val popupEvent = event(action, file, ActionPlaces.EDITOR_POPUP)
        action.update(popupEvent)
        assertEquals("enabled from popup", enabled, popupEvent.presentation.isEnabled)
        assertEquals("visible in popup", visibleInPopup, popupEvent.presentation.isVisible)

        val shortcutEvent = event(action, file, ActionPlaces.KEYBOARD_SHORTCUT)
        action.update(shortcutEvent)
        assertEquals("enabled from shortcut", enabled, shortcutEvent.presentation.isEnabled)
    }

    private fun event(action: AnAction, file: VirtualFile?, place: String): AnActionEvent {
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()
        val uiKind = if (place == ActionPlaces.EDITOR_POPUP) ActionUiKind.POPUP else ActionUiKind.NONE
        return AnActionEvent.createEvent(context, Presentation().apply { copyFrom(action.templatePresentation) },
            place, uiKind, null)
    }

    private fun file(path: String): VirtualFile =
        myFixture.findFileInTempDir(path) ?: myFixture.addFileToProject(path, "").virtualFile

}
