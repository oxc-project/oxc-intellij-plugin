package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonNumberLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory

/** The config names that the plugin can read. The server also reads [OXFMT_JS_CONFIG_NAMES]. */
val OXFMT_JSON_CONFIG_NAMES: List<String> = listOf(".oxfmtrc.json", ".oxfmtrc.jsonc")

val OXFMT_JS_CONFIG_NAMES: List<String> = listOf("oxfmt.config.ts", "oxfmt.config.mts")

val OXFMT_CONFIG_NAMES: List<String> = OXFMT_JSON_CONFIG_NAMES + OXFMT_JS_CONFIG_NAMES

private val JS_CONFIG_EXTENSIONS = setOf("js", "mjs", "cjs", "ts", "mts", "cts")

/** The server reads any file that isn't JavaScript or TypeScript as JSON, which is all the plugin can read. */
fun isReadableOxfmtConfig(file: VirtualFile): Boolean = file.extension?.lowercase() !in JS_CONFIG_EXTENSIONS

enum class OxfmtTrailingComma { NONE, ALL, ES5 }

/** The Oxfmt options that have a matching IDE code style setting, with Oxfmt's defaults. */
data class OxfmtConfig(
  val bracketSpacing: Boolean = true,
  val printWidth: Int = 100,
  val semi: Boolean = true,
  val singleQuote: Boolean = false,
  val tabWidth: Int = 2,
  val trailingComma: OxfmtTrailingComma = OxfmtTrailingComma.ALL,
  val useTabs: Boolean = false,
) {
  companion object {

    /**
     * Reads the file's latest text. The file's own PSI can lag behind a change made outside the IDE until
     * its document is committed, and the settings computed from it would then stay out of date.
     */
    fun read(project: Project, file: VirtualFile): OxfmtConfig? {
      if (!isReadableOxfmtConfig(file)) return null
      val text = FileDocumentManager.getInstance().getCachedDocument(file)?.text ?: VfsUtilCore.loadText(file)
      // As JSON whatever the name, because a manual config can be called anything. JSON's parser accepts comments.
      return parse(PsiFileFactory.getInstance(project).createFileFromText(file.name, JsonFileType.INSTANCE, text))
    }

    /** A value of the wrong type, or an unknown name, is treated as if it wasn't there. */
    fun parse(file: PsiFile): OxfmtConfig {
      val root = (file as? JsonFile)?.topLevelValue as? JsonObject ?: return OxfmtConfig()
      val defaults = OxfmtConfig()

      fun boolean(key: String, default: Boolean) =
        (root.findProperty(key)?.value as? JsonBooleanLiteral)?.value ?: default

      fun int(key: String, default: Int) =
        (root.findProperty(key)?.value as? JsonNumberLiteral)?.value?.toInt() ?: default

      val trailingComma = (root.findProperty("trailingComma")?.value as? JsonStringLiteral)?.value
      return OxfmtConfig(
        bracketSpacing = boolean("bracketSpacing", defaults.bracketSpacing),
        printWidth = int("printWidth", defaults.printWidth),
        semi = boolean("semi", defaults.semi),
        singleQuote = boolean("singleQuote", defaults.singleQuote),
        tabWidth = int("tabWidth", defaults.tabWidth),
        trailingComma = OxfmtTrailingComma.entries.find { it.name.lowercase() == trailingComma } ?: defaults.trailingComma,
        useTabs = boolean("useTabs", defaults.useTabs),
      )
    }
  }
}
