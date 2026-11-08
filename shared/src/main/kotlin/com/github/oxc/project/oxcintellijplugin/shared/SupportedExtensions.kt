package com.github.oxc.project.oxcintellijplugin.shared

/** One rule for both tools: a leading dot, then letters, digits, `-` or `_`. */
private val EXTENSION_PATTERN = Regex("^\\.[a-zA-Z0-9_-]+$")

fun isValidExtension(extension: String): Boolean = EXTENSION_PATTERN.matches(extension)

/** Only the file's last extension is compared. An empty list of [configured] extensions means [defaults]. */
fun isSupportedExtension(configured: List<String>, defaults: List<String>, extension: String?): Boolean =
  extension != null && ".$extension" in configured.ifEmpty { defaults }
