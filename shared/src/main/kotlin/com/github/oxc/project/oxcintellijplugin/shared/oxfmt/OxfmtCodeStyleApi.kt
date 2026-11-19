package com.github.oxc.project.oxcintellijplugin.shared.oxfmt

import kotlinx.serialization.Serializable

/**
 * Why the backend didn't read a config. [configName] is for the title, and [configUrl] is the backend's
 * URL for it. It isn't a `VirtualFileId`, because one made on the backend reaches a thin client empty.
 */
@Serializable
sealed interface OxfmtCodeStyleNotice {
  val configUrl: String
  val configName: String

  /** Reading it would run the project's code, which only happens in a trusted project. */
  @Serializable
  data class Untrusted(override val configUrl: String, override val configName: String) : OxfmtCodeStyleNotice

  /** [message] is what went wrong, and [details] is everything known about it, such as the stack. */
  @Serializable
  data class Failed(
    override val configUrl: String,
    override val configName: String,
    val reason: OxfmtJsConfigFailure,
    val message: String,
    val details: String,
  ) : OxfmtCodeStyleNotice
}

@Serializable
enum class OxfmtJsConfigFailure {

  /** No Node interpreter, or one that isn't valid. */
  NO_NODE,

  /** The interpreter can't load TypeScript, which needs Node.js 22.18 or later. */
  OLD_NODE,

  /** The config threw, `vite-plus` couldn't be loaded, Node timed out, or its answer made no sense. */
  ERROR,
}
