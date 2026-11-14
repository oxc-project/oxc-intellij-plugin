package com.github.oxc.project.oxcintellijplugin.shared

import com.intellij.openapi.util.IconLoader

/** In the shared module, so that an icon the backend hands out can be loaded on the client too. */
object OxcIcons {

  @JvmField
  val OxcRound = IconLoader.getIcon("/icons/oxcRound.svg", OxcIcons::class.java)
}
