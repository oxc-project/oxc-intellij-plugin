package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.frontend.settings.canGoToActionsOnSave
import com.intellij.ide.actionsOnSave.ActionsOnSaveConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ex.Settings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * The pages' link to Actions on Save selects the page in the open settings. A thin client's settings
 * don't have the backend's page, and there the row says where to find it instead.
 */
class ActionsOnSaveLinkTest : BasePlatformTestCase() {

  fun testTheLinkStaysWhenTheSettingsHaveThePage() {
    assertTrue(canGoToActionsOnSave(settingsWith(ActionsOnSaveConfigurable(project))))
  }

  fun testTheLinkGoesWhenTheSettingsDoNotHaveThePage() {
    assertFalse(canGoToActionsOnSave(settingsWith()))
  }

  fun testTheLinkStaysWhenTheSettingsAreUnknown() {
    assertTrue(canGoToActionsOnSave(null))
  }

  private fun settingsWith(vararg pages: Configurable): Settings = Pages(listOf(Group(pages)))

  private class Group(private val pages: Array<out Configurable>) : ConfigurableGroup {
    override fun getDisplayName(): String = "Pages"
    override fun getConfigurables(): Array<Configurable> = arrayOf(*pages)
  }

  private class Pages(groups: List<ConfigurableGroup>) : Settings(groups) {
    override fun selectImpl(configurable: Configurable?): Promise<in Any> = resolvedPromise()
    override fun getConfigurableWithInitializedUiComponentImpl(configurable: Configurable, initializeUiComponentIfNotYet: Boolean): Configurable? = null
    override fun checkModifiedImpl(configurable: Configurable) = Unit
    override fun setSearchText(option: String?) = Unit
  }
}
