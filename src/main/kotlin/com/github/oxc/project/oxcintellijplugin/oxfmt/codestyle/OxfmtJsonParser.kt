package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig.TrailingCommaOption
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonLiteral
import com.intellij.json.psi.JsonNullLiteral
import com.intellij.json.psi.JsonNumberLiteral
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

@Service(Service.Level.PROJECT)
class OxfmtJsonParser(private val project: Project) {

    fun parseConfigFile(virtualFile: VirtualFile): OxfmtConfig {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                      ?: return OxfmtConfig.DEFAULT

        return parseConfigFile(psiFile)
    }

    fun parseConfigFile(psiFile: PsiFile): OxfmtConfig {
        if (psiFile !is JsonFile) return OxfmtConfig.DEFAULT

        val topLevelObject = psiFile.topLevelValue
        if (topLevelObject !is JsonObject) return OxfmtConfig.DEFAULT

        val oxfmtDefault = OxfmtConfig.DEFAULT
        val bracketSameLine = readProperty<JsonBooleanLiteral>(topLevelObject,
            "bracketSameLine")?.value ?: oxfmtDefault.bracketSameLine
        val bracketSpacing = readProperty<JsonBooleanLiteral>(topLevelObject,
            "bracketSpacing")?.value ?: oxfmtDefault.bracketSpacing
        val lineSeparator = readProperty<JsonStringLiteral>(topLevelObject, "endOfLine")?.value
                            ?: oxfmtDefault.lineSeparator
        val printWidth = readProperty<JsonNumberLiteral>(topLevelObject,
            "printWidth")?.value?.toInt() ?: oxfmtDefault.printWidth
        val semi = readProperty<JsonBooleanLiteral>(topLevelObject, "semi")?.value
                   ?: oxfmtDefault.semi
        val singleQuote = readProperty<JsonBooleanLiteral>(topLevelObject, "singleQuote")?.value
                          ?: oxfmtDefault.singleQuote
        val tabWidth = readProperty<JsonNumberLiteral>(topLevelObject, "tabWidth")?.value?.toInt()
                       ?: oxfmtDefault.tabWidth
        val trailingComma = TrailingCommaOption.valueOf(
            readProperty<JsonStringLiteral>(topLevelObject, "trailingComma")?.value
            ?: oxfmtDefault.trailingComma.name)
        val useTabs = readProperty<JsonBooleanLiteral>(topLevelObject, "useTabs")?.value
                      ?: oxfmtDefault.useTabs

        return OxfmtConfig(bracketSameLine = bracketSameLine, bracketSpacing = bracketSpacing,
            lineSeparator = lineSeparator, printWidth = printWidth, semi = semi,
            singleQuote = singleQuote, tabWidth = tabWidth, trailingComma = trailingComma,
            useTabs = useTabs)
    }

    private fun <T : JsonLiteral> readProperty(jsonObject: JsonObject, key: String): T? {
        val value = jsonObject.findProperty(key)?.value ?: return null
        if (value is JsonNullLiteral) {
            return null
        }

        return try {
            value as T
        } catch (_: ClassCastException) {
            null
        }
    }

}
