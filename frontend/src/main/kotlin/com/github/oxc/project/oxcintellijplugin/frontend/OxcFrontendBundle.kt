package com.github.oxc.project.oxcintellijplugin.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.OxcFrontendBundle"

internal object OxcFrontendBundle : DynamicBundle(OxcFrontendBundle::class.java, BUNDLE) {

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    getMessage(key, *params)
}
