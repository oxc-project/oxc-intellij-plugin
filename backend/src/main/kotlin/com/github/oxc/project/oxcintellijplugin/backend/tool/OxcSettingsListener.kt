package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.intellij.util.messages.Topic

fun interface OxcSettingsListener {

  fun settingsChanged(tool: OxcTool, affectsServers: Boolean)

  companion object {
    @Topic.ProjectLevel
    val TOPIC: Topic<OxcSettingsListener> =
      Topic(OxcSettingsListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
