package com.github.oxc.project.oxcintellijplugin.backend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.OxcBackendBundle"

internal object OxcBackendBundle : DynamicBundle(OxcBackendBundle::class.java, BUNDLE) {

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    getMessage(key, *params)
}
