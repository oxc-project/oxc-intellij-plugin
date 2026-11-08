package com.github.oxc.project.oxcintellijplugin.shared

import kotlinx.serialization.Serializable

/**
 * The tools that the plugin runs. Vite+ isn't one of them, because it's another way of launching the
 * same two servers, and only the backend's package lookup needs to know about it.
 */
@Serializable
enum class OxcTool {
  OXLINT,
  OXFMT,
}
