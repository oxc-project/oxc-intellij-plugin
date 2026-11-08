package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.lsp.OxfmtLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

/**
 * Both real servers on one save. Oxlint has to change the file first, and Oxfmt has to format what that
 * left, so the saved text is only right if they ran in that order on up to date text.
 */
class SaveActionsOrderTest : OxcFixtureTestCase() {

  fun testOxlintFixesThenOxfmtFormats() {
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    myFixture.testDataPath = "src/test/testData"
    myFixture.copyDirectoryToProject("oxlint/highlighting/no-config", "")
    myFixture.copyDirectoryToProject("oxfmt/formatting/nested-config", "")
    myFixture.addFileToProject(".oxlintrc.json", """{ "rules": { "no-var": "error" } }""")
    val file = myFixture.addFileToProject("fix.js", "var fixed = \"a\"\nconsole.log(fixed)\n").virtualFile
    OxlintSettings.getInstance(project).state.fixAllOnSave = true
    OxfmtSettings.getInstance(project).state.fixAllOnSave = true

    myFixture.configureFromExistingVirtualFile(file)
    waitUntil("both servers are running") {
      val manager = LspClientManager.getInstance(project)
      manager.getClients(OxlintLspIntegrationProvider::class.java).any { it.state == LspServerState.Running } &&
      manager.getClients(OxfmtLspIntegrationProvider::class.java).any { it.state == LspServerState.Running }
    }
    waitUntil("Oxlint has reported diagnostics") {
      myFixture.doHighlighting().any { "no-var" in it.description.orEmpty() || "`var`" in it.description.orEmpty() }
    }
    WriteAction.runAndWait<Exception> {
      CommandProcessor.getInstance().runUndoTransparentAction { myFixture.editor.document.insertString(0, "// edited\n") }
    }

    FileDocumentManager.getInstance().saveAllDocuments()

    waitUntil("the fixed and formatted text has been saved") {
      String(file.contentsToByteArray()) == "// edited\nconst fixed = 'a';\nconsole.log(fixed);\n"
    }
  }
}
