package com.github.oxc.project.oxcintellijplugin.oxfmt

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val OXFMT_BUNDLE = "messages.OxfmtBundle"

object OxfmtBundle : DynamicBundle(OXFMT_BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = OXFMT_BUNDLE) key: String,
        vararg params: Any) =
        getMessage(key, *params)

    @Suppress("unused")
    @JvmStatic
    fun messagePointer(@PropertyKey(resourceBundle = OXFMT_BUNDLE) key: String,
        vararg params: Any) =
        getLazyMessage(key, *params)
}
