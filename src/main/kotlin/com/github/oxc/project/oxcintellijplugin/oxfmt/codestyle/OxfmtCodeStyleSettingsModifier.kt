package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.extensions.findNearestOxfmtJsonConfigFile
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtBundle
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtConfigurable
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.ide.DataManager
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings
import com.intellij.lang.typescript.formatter.TypeScriptCodeStyleSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.function.Consumer
import org.jetbrains.annotations.Nls

@Suppress("UnstableApiUsage")
class OxfmtCodeStyleSettingsModifier : CodeStyleSettingsModifier {

    override fun modifySettings(settings: TransientCodeStyleSettings, psiFile: PsiFile): Boolean {
        val project = psiFile.project
        val file = psiFile.virtualFile ?: return false

        if (!application.isReadAccessAllowed) {
            return false
        }

        if (project.isDisposed) {
            return false
        }

        val oxfmtSettings = OxfmtSettings.getInstance(project)
        if (!oxfmtSettings.preferOxfmtCodeStyleSettings) {
            return false
        }
        if (!oxfmtSettings.fileSupported(file)) {
            return false
        }

        return doModifySettings(settings, psiFile)
    }

    override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return OxfmtBundle.message("oxfmt.code.style.display.name")
    }

    override fun getStatusBarUiContributor(
        transientSettings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor {
        return OxfmtCodeStyleStatusBarUiContributor()
    }

    override fun mayOverrideSettingsOf(project: Project): Boolean {
        return OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings
    }

    override fun getDisablingFunction(project: Project): Consumer<CodeStyleSettings?> {
        return Consumer { _: CodeStyleSettings? ->
            OxfmtSettings.getInstance(project).preferOxfmtCodeStyleSettings = false
            DataManager.getInstance().dataContextFromFocusAsync.then { dataContext ->
                val settings = Settings.KEY.getData(dataContext) ?: return@then
                val configurable = settings.getConfigurableWithInitializedUiComponent(
                    OxfmtConfigurable.CONFIGURABLE_ID, false) ?: return@then
                val unwrapped = (configurable as? ConfigurableWrapper)?.rawConfigurable
                                ?: configurable
                val oxfmtConfigurable = unwrapped as? OxfmtConfigurable ?: return@then
                oxfmtConfigurable.preferOxfmtCodeStyleSettingsCheckBox.isSelected = false
            }
        }
    }

    @RequiresReadLock
    private fun doModifySettings(settings: TransientCodeStyleSettings, psiFile: PsiFile): Boolean {
        val configFile = psiFile.virtualFile.findNearestOxfmtJsonConfigFile(
            psiFile.project.guessProjectDir()) ?: return false
        val project = psiFile.project
        val config = project.service<OxfmtJsonParser>().parseConfigFile(configFile)

//        var changedSettings = applyBasicOxfmtMappings(settings, config)
        var changedSettings = false
        for (configurator in getAdvancedCodeStyleConfigurators()) {
            if (!configurator.isAlreadyApplied(settings, config)) {
                configurator.applySettings(settings, config)
                changedSettings = true
            }
        }


        if (changedSettings) {
            thisLogger().debug("Modified settings for ${psiFile.name}")
            return true
        } else {
            thisLogger().debug("No changes for ${psiFile.name}")
            return false
        }
    }

    // TODO: Is this needed if Oxfmt only supports JS/TS?
    //  Maybe it is for the delegated Prettier support that the CLI offers, but the language server doesn't?
//    private fun applyBasicOxfmtMappings(settings: TransientCodeStyleSettings,
//        config: OxfmtConfig): Boolean {
//        var changedSettings = false
//
//        val basicPropertyMap = mapOf(
//            "continuation_indent_size" to config.tabWidth.toString(),
//            "end_of_line" to config.lineSeparator.toString().lowercase(),
//            "indent_size" to config.tabWidth.toString(),
//            "indent_style" to if (config.useTabs) "tab" else "space",
//            "tab_width" to config.tabWidth.toString(),
//            "visual_guides" to config.printWidth.toString(),
//        )
//
//        getAllMappers(settings).forEach { mapper ->
//            for ((propertyName, value) in basicPropertyMap) {
//                mapper.getAccessor(propertyName)?.let {
//                    changedSettings = changedSettings or it.setFromString(value)
//                }
//            }
//        }
//
//        return changedSettings
//    }

    private fun getAdvancedCodeStyleConfigurators(): List<OxfmtCodeStyleConfigurator> {
        return listOf(
            JsOxfmtCodeStyleConfigurator(JavascriptLanguage, TypeScriptCodeStyleSettings::class),
            JsOxfmtCodeStyleConfigurator(JavaScriptSupportLoader.TYPESCRIPT,
                JSCodeStyleSettings::class),
        )
    }

//    private fun getAllMappers(
//        settings: TransientCodeStyleSettings): Collection<AbstractCodeStylePropertyMapper> {
//        return buildSet {
//            addAll(LanguageCodeStyleSettingsProvider.getAllProviders()
//                .map { it.getPropertyMapper(settings) })
//            add(GeneralCodeStylePropertyMapper(settings))
//        }
//    }
}
