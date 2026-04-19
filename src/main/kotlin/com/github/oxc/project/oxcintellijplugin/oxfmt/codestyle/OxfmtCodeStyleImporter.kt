package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.extensions.isOxfmtConfigFile
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.application.options.CodeStyle
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.lang.javascript.linter.JSLinterCodeStyleImporter
import com.intellij.lang.javascript.linter.JSNpmLinterState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

class OxfmtCodeStyleImporter(isForInitialImport: Boolean) :
    JSLinterCodeStyleImporter<OxfmtConfig>(isForInitialImport) {

    override fun createSettingsConfigurable(project: Project): Configurable {
        return OxfmtConfigurable(project)
    }

    override fun getStoredState(project: Project?): JSNpmLinterState<*> {
        return OxfmtSettings.getInstance(project!!)
    }

    override fun getNpmPackageName(): String {
        return OxfmtPackage.PACKAGE_NAME
    }

    override fun getToolName(): @NlsContexts.NotificationContent String {
        return OxfmtBundle.message("oxfmt.name")
    }

    override fun isDirectlyImportable(configPsi: PsiFile, parsedConfig: OxfmtConfig?): Boolean {
        return parsedConfig != null
    }

    override fun parseConfigFromFile(configPsi: PsiFile): OxfmtConfig? {
        return ReadAction.compute<OxfmtConfig?, RuntimeException> {
            val virtualFile = configPsi.virtualFile
            if (!virtualFile.isOxfmtConfigFile()) {
                return@compute null
            }

            return@compute CachedValuesManager.getCachedValue(configPsi) {
                return@getCachedValue CachedValueProvider.Result.create(
                    parseConfigInternal(virtualFile), configPsi)
            }
        }
    }

    override fun computeEffectiveConfig(configPsi: PsiFile, nodeJsInterpreter: NodeJsInterpreter,
        oxfmtNodePackage: NodePackage): OxfmtConfig? {
        return parseConfigFromFile(configPsi)
    }

    override fun importConfig(configPsi: PsiFile, parsedConfig: OxfmtConfig): ImportResult {
        val jsOxfmtCodeStyleInstaller = JsOxfmtCodeStyleInstaller()
        if (jsOxfmtCodeStyleInstaller.isInstalled(configPsi.project, parsedConfig,
                CodeStyle.getSettings(configPsi.project))
        ) {
            return ImportResult.alreadyImported()
        }
        jsOxfmtCodeStyleInstaller.install(configPsi.project, parsedConfig,
            CodeStyle.getSettings(configPsi.project))
        return ImportResult.success(emptyList())
    }

    private fun parseConfigInternal(configFile: VirtualFile): OxfmtConfig {
        return OxfmtConfig.fromGsonJsonObject(
            Gson().fromJson(configFile.readText(), JsonObject::class.java))
    }
}
