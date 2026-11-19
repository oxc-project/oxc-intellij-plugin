package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspIntegrationProvider
import com.github.oxc.project.oxcintellijplugin.frontend.oxlint.settings.OxlintConfigurable
import com.github.oxc.project.oxcintellijplugin.shared.OxcIcons
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.options.Configurable
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState

/** Starts a real Oxlint server. Needs Node on PATH and `npm ci` run in the fixture folder. */
class ToolWidgetItemTest : OxcFixtureTestCase() {

  fun testOxlintItemHasTheOxcIcon() {
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.configureFromTempProjectFile("index.js").virtualFile
    val provider = OxlintLspIntegrationProvider()

    val item = provider.createWidgetItem(runningClient(provider), file)

    assertSame(OxcIcons.OxcRound, item.statusBarIcon)
  }

  /** The platform adds this button only for an item that names its page's class, which ours can't. */
  fun testItemHasASettingsButtonAfterThePlatformsButtons() {
    myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
    myFixture.copyDirectoryToProject("no-config", "")
    val file = myFixture.configureFromTempProjectFile("index.js").virtualFile
    val provider = OxlintLspIntegrationProvider()

    val buttons = inlineActions(provider.createWidgetItem(runningClient(provider), file).createWidgetAction())

    assertTrue("The platform's restart button should still be there: $buttons", buttons.size >= 2)
    assertSame(AllIcons.General.Settings, buttons.last().templatePresentation.icon)
    assertEquals("Open Settings", buttons.last().templatePresentation.text)
  }

  /** The item opens the page by its id, because the backend can't name the frontend's class. */
  fun testEachToolsPageIdNamesItsSettingsPage() {
    assertEquals(OxlintConfigurable::class.java.name, pageClassName(OxlintDefinition))
  }

  private fun pageClassName(definition: ToolDefinition): String? =
    Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
      .singleOrNull { it.id == definition.configurableId }
      ?.instanceClass

  /** The new UI puts the buttons on the item's action, and the old UI makes the item a group of them. */
  private fun inlineActions(action: AnAction): List<AnAction> =
    action.templatePresentation.getClientProperty(ActionUtil.INLINE_ACTIONS)
    ?: (action as DefaultActionGroup).getChildren(ActionManager.getInstance()).toList()

  private fun runningClient(provider: ToolLspIntegrationProvider): LspClient {
    fun running() = LspClientManager.getInstance(project).getClients(provider.javaClass)
      .filter { it.state == LspServerState.Running }
    waitUntil("a server is running") { running().size == 1 }
    return running().single()
  }
}
