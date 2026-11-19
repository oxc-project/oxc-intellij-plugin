package com.github.oxc.project.oxcintellijplugin.settings

import com.github.oxc.project.oxcintellijplugin.backend.rpc.OxcProjectDisposable
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsApi
import com.github.oxc.project.oxcintellijplugin.shared.oxlint.settings.OxlintSettingsData
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking

/** Each test gets its own project, so ending the plugin's lifetime in it leaves no other test without one. */
class SettingsUpdatesTest : OxcFixtureTestCase() {

  /** What a plugin unload does. */
  fun testUpdatesEndWhenThePluginsLifetimeInTheProjectEnds() {
    val projectId = project.projectId()
    val received = CopyOnWriteArrayList<OxlintSettingsData>()
    val collect = ApplicationManager.getApplication().executeOnPooledThread {
      runBlocking { OxlintSettingsApi.getInstance().updates(projectId).collect { received += it } }
    }
    waitUntil("the current settings arrive") { received.isNotEmpty() }

    Disposer.dispose(OxcProjectDisposable.getInstance(project))

    PlatformTestUtil.waitForFuture(collect, 30_000)
  }
}
