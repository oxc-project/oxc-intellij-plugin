package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.application.options.CodeStyle
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings.TrailingCommaOption
import com.intellij.lang.typescript.formatter.TypeScriptCodeStyleSettings
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings

/** Applying `.oxfmtrc.json` settings to IntelliJ's JavaScript/TypeScript code style. */
class OxfmtCodeStyleTest : OxcLightTestCase() {

    private val customConfig = """
        {
          "singleQuote": true,
          "semi": false,
          "bracketSpacing": false,
          "trailingComma": "none",
          "tabWidth": 4,
          "useTabs": true,
          "printWidth": 120
        }
    """.trimIndent()

    fun testImporterAppliesTheConfigToJavaScriptAndTypeScript() {
        val config = addConfig(customConfig)
        val settings = CodeStyle.getSettings(project)

        assertTrue(OxfmtCodeStyleImporter(false).importConfigFile(config))

        assertCustomSettingsApplied(settings.getCustomSettings(JSCodeStyleSettings::class.java))
        assertCustomSettingsApplied(settings.getCustomSettings(TypeScriptCodeStyleSettings::class.java))
        for (language in listOf(JavascriptLanguage, JavaScriptSupportLoader.TYPESCRIPT)) {
            val indent = settings.getCommonSettings(language).indentOptions!!
            assertEquals(4, indent.INDENT_SIZE)
            assertEquals(4, indent.CONTINUATION_INDENT_SIZE)
            assertEquals(4, indent.TAB_SIZE)
            assertTrue(indent.USE_TAB_CHARACTER)
            assertEquals(120, settings.getRightMargin(language))
            assertEquals(listOf(120), settings.getSoftMargins(language))
        }
    }

    fun testInstallerReportsWhetherTheConfigIsAlreadyApplied() = withTemporaryCodeStyle { settings ->
        val config = parse(customConfig)
        val installer = JsOxfmtCodeStyleInstaller()

        assertFalse(installer.isInstalled(project, config, settings))
        installer.install(project, config, settings)
        assertTrue(installer.isInstalled(project, config, settings))

        settings.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE = 8
        assertFalse(installer.isInstalled(project, config, settings))
    }

    fun testDefaultConfigUsesDoubleQuotesSemicolonsAndTrailingCommas() = withTemporaryCodeStyle { settings ->
        JsOxfmtCodeStyleInstaller().install(project, OxfmtConfig.DEFAULT, settings)

        val js = settings.getCustomSettings(JSCodeStyleSettings::class.java)
        assertTrue(js.USE_DOUBLE_QUOTES)
        assertTrue(js.USE_SEMICOLON_AFTER_STATEMENT)
        assertTrue(js.SPACES_WITHIN_OBJECT_LITERAL_BRACES)
        assertEquals(TrailingCommaOption.WhenMultiline, js.ENFORCE_TRAILING_COMMA)
        assertEquals(2, settings.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE)
        assertEquals(100, settings.getRightMargin(JavascriptLanguage))
    }

    fun testModifierDoesNothingUnlessPreferred() {
        addConfig(customConfig)
        val file = addFile("index.js", "")

        assertFalse(OxfmtCodeStyleSettingsModifier().mayOverrideSettingsOf(project))
        assertFalse(modify(file).first)
    }

    fun testModifierAppliesTheNearestConfigWhenPreferred() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true
        addConfig(customConfig)
        val file = addFile("index.js", "")

        assertTrue(OxfmtCodeStyleSettingsModifier().mayOverrideSettingsOf(project))
        val (modified, settings) = modify(file)

        assertTrue(modified)
        assertCustomSettingsApplied(settings.getCustomSettings(JSCodeStyleSettings::class.java))
        assertCustomSettingsApplied(settings.getCustomSettings(TypeScriptCodeStyleSettings::class.java))
    }

    fun testModifierIgnoresUnsupportedFiles() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true
        addConfig(customConfig)
        val file = addFile("main.rs", "")

        assertFalse(modify(file).first)
    }

    fun testModifierDoesNothingWithoutAConfigFile() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true
        val file = addFile("index.js", "")

        assertFalse(modify(file).first)
    }

    /**
     * QUESTIONABLE: the modifier, which is what "Prefer Oxfmt configuration to IDE code style"
     * turns on, only applies quotes, semicolons, bracket spacing and trailing commas. Unlike the
     * importer it never applies `tabWidth`, `useTabs` or `printWidth` (that code is commented out),
     * so the IDE keeps its own indentation and wrapping for the file.
     */
    fun testModifierDoesNotApplyIndentationOrPrintWidth() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true
        addConfig(customConfig)
        val file = addFile("index.js", "")
        val before = CodeStyle.getSettings(project)

        val (_, settings) = modify(file)

        val indent = settings.getCommonSettings(JavascriptLanguage).indentOptions!!
        assertEquals(before.getCommonSettings(JavascriptLanguage).indentOptions!!.INDENT_SIZE, indent.INDENT_SIZE)
        assertFalse(indent.USE_TAB_CHARACTER)
        assertEquals(before.getRightMargin(JavascriptLanguage), settings.getRightMargin(JavascriptLanguage))
    }

    /**
     * QUESTIONABLE: `bracketSameLine` and `endOfLine` are parsed but neither the importer nor the
     * modifier applies them.
     */
    fun testBracketSameLineAndEndOfLineAreNotApplied() = withTemporaryCodeStyle { settings ->
        val lineSeparatorBefore = settings.lineSeparator

        JsOxfmtCodeStyleInstaller().install(project,
            parse("""{ "endOfLine": "crlf", "bracketSameLine": true }"""), settings)

        assertEquals(lineSeparatorBefore, settings.lineSeparator)
    }

    fun testModifierDisablingFunctionTurnsThePreferenceOff() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true

        OxfmtCodeStyleSettingsModifier().getDisablingFunction(project).accept(null)

        assertFalse(OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings)
    }

    fun testModifierName() {
        assertEquals("Oxfmt", OxfmtCodeStyleSettingsModifier().name)
    }

    fun testStatusBarContributorOffersConfigAndSettingsActions() {
        val file = addFile("index.js", "")
        val contributor = OxfmtCodeStyleStatusBarUiContributor()

        assertTrue(contributor.areActionsAvailable(file.virtualFile))
        assertEquals(listOf("Edit Configuration File", "Open Oxfmt Settings"),
            contributor.getActions(file).map { it!!.templatePresentation.text })
        assertEquals("Oxfmt", contributor.actionGroupTitle)
    }

    fun testStatusBarDisableActionTurnsThePreferenceOff() {
        OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = true
        val action = OxfmtCodeStyleStatusBarUiContributor().createDisableAction(project)

        assertEquals("Disable Oxfmt Code Style for Current Project", action.templatePresentation.text)
        myFixture.testAction(action)

        assertFalse(OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings)
    }

    fun testImportCodeStyleActionAppliesTheNearestConfig() {
        addConfig(customConfig)
        myFixture.configureFromExistingVirtualFile(addFile("src/index.js", "").virtualFile)

        myFixture.testAction(ActionManager.getInstance().getAction("OxfmtImportCodeStyleAction"))

        val settings = CodeStyle.getSettings(project)
        assertCustomSettingsApplied(settings.getCustomSettings(JSCodeStyleSettings::class.java))
        assertCustomSettingsApplied(settings.getCustomSettings(TypeScriptCodeStyleSettings::class.java))
    }

    fun testStatusBarEditConfigurationActionOpensTheNearestConfig() {
        val config = addConfig(customConfig)
        val file = addFile("src/index.js", "")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)

        myFixture.testAction(OxfmtCodeStyleStatusBarUiContributor().getActions(file)[0]!!)

        assertContainsElements(FileEditorManager.getInstance(project).openFiles.toList(), config.virtualFile)
    }

    fun testStatusBarEditConfigurationActionWithoutAConfigNotifies() {
        val file = addFile("src/index.js", "")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val notifications = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications.add(notification)
            }
        })

        myFixture.testAction(OxfmtCodeStyleStatusBarUiContributor().getActions(file)[0]!!)

        assertEquals(listOf("Oxfmt configuration file not found"), notifications.map { it.title })
    }

    private fun assertCustomSettingsApplied(settings: JSCodeStyleSettings) {
        assertFalse(settings.USE_DOUBLE_QUOTES)
        assertFalse(settings.USE_SEMICOLON_AFTER_STATEMENT)
        assertFalse(settings.SPACES_WITHIN_OBJECT_LITERAL_BRACES)
        assertFalse(settings.SPACES_WITHIN_OBJECT_TYPE_BRACES)
        assertFalse(settings.SPACES_WITHIN_IMPORTS)
        assertEquals(TrailingCommaOption.Remove, settings.ENFORCE_TRAILING_COMMA)
        assertTrue(settings.FORCE_QUOTE_STYlE)
        assertTrue(settings.FORCE_SEMICOLON_STYLE)
        assertTrue(settings.SPACE_BEFORE_FUNCTION_LEFT_PARENTH)
    }

    private fun modify(file: PsiFile): Pair<Boolean, TransientCodeStyleSettings> {
        val settings = TransientCodeStyleSettings(file, CodeStyle.getSettings(project))
        val modified = com.intellij.openapi.application.ReadAction.compute<Boolean, Throwable> {
            OxfmtCodeStyleSettingsModifier().modifySettings(settings, file)
        }
        return modified to settings
    }

    /** Parses [text] from a file the config watcher ignores, so nothing is imported implicitly. */
    private fun parse(text: String): OxfmtConfig =
        project.service<OxfmtJsonParser>().parseConfigFile(addFile("parsed-oxfmt-config.json", text))

    /**
     * Adds `.oxfmtrc.json` and then resets the code style. Adding the file makes
     * `OxfmtConfigWatcher` import it into the project code style (see `OxfmtConfigWatcherTest`),
     * which would hide what the code under test does.
     */
    private fun addConfig(text: String): PsiFile {
        val config = addFile(".oxfmtrc.json", text)
        CodeStyle.setTemporarySettings(project, CodeStyle.createTestSettings())
        return config
    }

    /** Runs [block] against the (temporary, see [OxcLightTestCase]) project code style. */
    private fun withTemporaryCodeStyle(block: (CodeStyleSettings) -> Unit) = block(CodeStyle.getSettings(project))
}
