package com.github.oxc.project.oxcintellijplugin.backend.oxlint

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.OxlintBundle"

internal object OxlintBundle : DynamicBundle(OxlintBundle::class.java, BUNDLE) {

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    getMessage(key, *params)
}
