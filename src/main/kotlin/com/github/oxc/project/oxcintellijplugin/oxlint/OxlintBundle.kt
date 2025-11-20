package com.github.oxc.project.oxcintellijplugin.oxlint

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val OXLINT_BUNDLE = "messages.OxlintBundle"

object OxlintBundle : DynamicBundle(OXLINT_BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = OXLINT_BUNDLE) key: String,
        vararg params: Any) =
        getMessage(key, *params)

    @Suppress("unused")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = OXLINT_BUNDLE) key: String,
        vararg params: Any) =
        getLazyMessage(key, *params)
}
