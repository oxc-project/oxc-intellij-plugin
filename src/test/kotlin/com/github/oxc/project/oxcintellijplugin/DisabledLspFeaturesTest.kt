package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.PlatformTestUtil

/**
 * Oxlint only provides diagnostics and code actions and Oxfmt only formatting; every other LSP
 * feature is switched off (`LspDisabledCustomization`) so the IDE's own JavaScript support is not
 * duplicated or overridden. Language features the platform adds later are enabled by default, so
 * these tests also fail when a new one appears that the plugin does not switch off.
 *
 * QUESTIONABLE: document links (`documentLinkCustomizer`, added to the platform after
 * `LspDisabledCustomization` was written) are not switched off, so a server that offers them is
 * asked for links in every open file. Neither Oxlint nor Oxfmt offers them today, so this has no
 * visible effect yet. The tests below expect document links until `LspDocumentLinkDisabled` is
 * added to `LspDisabledCustomization`.
 */
class DisabledLspFeaturesTest : OxcLspTestCase() {

    fun testOxlintEnablesOnlyDiagnosticsAndCodeActions() {
        setUpFakeServer(oxfmt = false)
        openFile("index.js")

        assertEquals(setOf("codeActionsCustomizer", "diagnosticsCustomizer", "documentLinkCustomizer"),
            enabledFeatures(waitForOxlintServer()))
    }

    fun testOxfmtEnablesOnlyFormatting() {
        setUpFakeServer(oxlint = false)
        openFile("index.js")

        assertEquals(setOf("formattingCustomizer", "documentLinkCustomizer"), enabledFeatures(waitForOxfmtServer()))
    }

    /** The server offers every feature, yet using them in the editor only asks it for diagnostics and code actions. */
    fun testOxlintServerIsOnlyAskedForDiagnosticsAndCodeActions() {
        setUpFakeServer(ALL_FEATURES, oxfmt = false)
        openFile("index.js")
        val server = waitForOxlintServer()

        useEditorFeatures()

        assertEquals(setOf("textDocument/codeAction", "textDocument/diagnostic", "textDocument/documentLink"),
            requestedFeatures(server))
    }

    /** The server offers every feature, yet using them in the editor only asks it to format. */
    fun testOxfmtServerIsOnlyAskedToFormat() {
        setUpFakeServer(ALL_FEATURES, oxlint = false)
        openFile("index.js")
        val server = waitForOxfmtServer()

        useEditorFeatures()

        assertEquals(setOf("textDocument/formatting", "textDocument/documentLink"), requestedFeatures(server))
    }

    /**
     * The feature customizers of [server] that are not one of the platform's `Lsp*Disabled` objects.
     * `symbolKindCustomizer` only maps symbol kinds to icons and cannot be disabled, so it is skipped.
     */
    private fun enabledFeatures(server: LspServer): Set<String> {
        val customization = server.descriptor.lspCustomization
        val customizers = LspCustomization::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.name.endsWith("Customizer") }
            .filter { it.name != "getSymbolKindCustomizer" }
        assertTrue("No customizers found on LspCustomization", customizers.size > 10)
        return customizers.filterNot { it.invoke(customization)::class.java.simpleName.endsWith("Disabled") }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercase) }
            .toSet()
    }

    /** Uses the editor features that an LSP server can provide on `index.js`. */
    private fun useEditorFeatures() {
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(editor.document.text.indexOf("a ="))
        myFixture.doHighlighting()
        myFixture.availableIntentions
        CodeFoldingManager.getInstance(project).updateFoldRegions(editor)
        myFixture.performEditorAction(IdeActions.ACTION_QUICK_JAVADOC)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_TYPE_DECLARATION)
        myFixture.performEditorAction(IdeActions.ACTION_HIGHLIGHT_USAGES_IN_FILE)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO)
        myFixture.testStructureView { }
        editor.caretModel.moveToOffset(editor.document.textLength)
        myFixture.type("a.")
        myFixture.completeBasic()
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
    }

    /**
     * The language feature requests [server] received, i.e. everything but lifecycle and document
     * sync messages. The server is stopped first: it logs messages in the order they arrive, so
     * once `exit` is logged, every earlier request is too.
     */
    private fun requestedFeatures(server: LspServer): Set<String> {
        LspServerManager.getInstance(project).stopServers(server.providerClass)
        PlatformTestUtil.waitWithEventsDispatching({ "Fake server log: ${fakeServerLog()}" },
            { "exit" in fakeServerLog() }, 30)
        return fakeServerLog().filterNot { it in LIFECYCLE_AND_SYNC || it.startsWith("$/") }.toSet()
    }

    companion object {
        private val LIFECYCLE_AND_SYNC = setOf("initialize", "initialized", "shutdown", "exit", "response",
            "workspace/didChangeConfiguration", "workspace/didChangeWatchedFiles", "textDocument/didOpen",
            "textDocument/didChange", "textDocument/didSave", "textDocument/didClose")

        /** Server capabilities for every LSP feature the platform can use. */
        private val ALL_FEATURES = """
            { "capabilities": {
                "hoverProvider": true,
                "completionProvider": { "triggerCharacters": ["."], "resolveProvider": true },
                "signatureHelpProvider": { "triggerCharacters": ["("] },
                "definitionProvider": true,
                "typeDefinitionProvider": true,
                "referencesProvider": true,
                "documentHighlightProvider": true,
                "documentSymbolProvider": true,
                "workspaceSymbolProvider": true,
                "documentLinkProvider": {},
                "colorProvider": true,
                "foldingRangeProvider": true,
                "selectionRangeProvider": true,
                "callHierarchyProvider": true,
                "typeHierarchyProvider": true,
                "inlayHintProvider": true,
                "codeLensProvider": {},
                "semanticTokensProvider": { "legend": { "tokenTypes": ["variable"], "tokenModifiers": [] }, "full": true },
                "executeCommandProvider": { "commands": ["oxc.fake"] }
            } }
        """.trimIndent()
    }
}
