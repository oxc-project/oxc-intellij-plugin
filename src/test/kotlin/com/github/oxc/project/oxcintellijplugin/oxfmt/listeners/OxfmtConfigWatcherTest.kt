package com.github.oxc.project.oxcintellijplugin.oxfmt.listeners

import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.application.options.CodeStyle
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil

/** Code style side effects of `OxfmtConfigWatcher`. Server restarts are covered by LSP tests. */
class OxfmtConfigWatcherTest : OxcLightTestCase() {

    fun testChangingTheConfigImportsItIntoTheCodeStyleWhenPreferred() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true
        assertTrue(jsSettings().USE_DOUBLE_QUOTES)

        editAndSaveConfig("""{ "singleQuote": true }""")

        assertFalse(jsSettings().USE_DOUBLE_QUOTES)
    }

    /**
     * QUESTIONABLE: the watcher imports every `.oxfmtrc.json` it sees into the project code style,
     * even when "Prefer Oxfmt configuration to IDE code style" is off and even when Oxfmt is
     * disabled, silently overwriting code style the user configured in the IDE.
     */
    fun testChangingTheConfigImportsItIntoTheCodeStyleEvenWhenNotPreferredOrDisabled() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = false
        OxfmtSettings.getInstance(project).configurationMode =
            com.github.oxc.project.oxcintellijplugin.ConfigurationMode.DISABLED
        assertTrue(jsSettings().USE_DOUBLE_QUOTES)

        editAndSaveConfig("""{ "singleQuote": true }""")

        assertFalse(jsSettings().USE_DOUBLE_QUOTES)
    }

    /**
     * QUESTIONABLE: deleting an `.oxfmtrc.json` logs an error. The watcher asks for the PSI of the
     * deleted (already invalid) file, which the platform reports as "Accessing invalid virtual
     * file". In the IDE this surfaces as an internal error notification.
     */
    fun testDeletingTheConfigLogsAnError() {
        val config = myFixture.addFileToProject(".oxfmtrc.json", "{}").virtualFile

        val error = LoggedErrorProcessor.executeAndReturnLoggedError {
            WriteAction.runAndWait<Throwable> { config.delete(this) }
        }

        assertNotNull("expected an error to be logged", error)
        assertTrue(error.message, error.message!!.contains("Accessing invalid virtual file"))
    }

    /** Edits `.oxfmtrc.json` in the editor and saves it, like a user would. */
    private fun editAndSaveConfig(text: String) {
        val config = addFile(".oxfmtrc.json", "{}")
        myFixture.configureFromExistingVirtualFile(config.virtualFile)
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText(text) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun jsSettings() = CodeStyle.getSettings(project).getCustomSettings(JSCodeStyleSettings::class.java)
}
