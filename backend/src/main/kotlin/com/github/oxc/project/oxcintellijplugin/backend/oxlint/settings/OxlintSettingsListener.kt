package com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings

import com.intellij.util.messages.Topic

fun interface OxlintSettingsListener {

  fun settingsChanged(affectsServers: Boolean)

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<OxlintSettingsListener> =
      Topic(OxlintSettingsListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
