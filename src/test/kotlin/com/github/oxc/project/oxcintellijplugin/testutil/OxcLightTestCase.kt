package com.github.oxc.project.oxcintellijplugin.testutil

import com.github.oxc.project.oxcintellijplugin.extensions.isOxfmtJsonConfigFile
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettingsState
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettingsState
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Light test case that resets the plugin's project settings before every test. Light tests share
 * one project, so settings changed by one test would otherwise leak into the next.
 */
abstract class OxcLightTestCase : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        OxlintSettings.getInstance(project).loadState(OxlintSettingsState())
        OxfmtSettings.getInstance(project).loadState(OxfmtSettingsState())
        // Creating an `.oxfmtrc.json` imports it into the project code style (OxfmtConfigWatcher), so
        // work on a throwaway copy of the code style settings.
        CodeStyle.setTemporarySettings(project,
            CodeStyleSettingsManager.getInstance(project).cloneSettings(CodeStyle.getSettings(project)))
    }

    /**
     * Adds a file and returns its PSI. `addFileToProject` creates an empty file and then writes its
     * content; `OxfmtConfigWatcher` reads the PSI of a new `.oxfmtrc.json` in between, which leaves
     * the PSI empty. Writing under a temporary name and renaming avoids that, matching a config
     * file that appears with its content already in place.
     */
    protected fun addFile(path: String, text: String): PsiFile {
        val file = myFixture.addFileToProject("$path.tmp", text).virtualFile
        WriteAction.runAndWait<Throwable> { file.rename(this, file.name.removeSuffix(".tmp")) }
        return PsiManager.getInstance(project).findFile(file)!!
    }

    override fun tearDown() {
        try {
            deleteOxfmtJsonConfigFiles()
            CodeStyle.dropTemporarySettings(project)
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Deleting an `.oxfmtrc.json` makes `OxfmtConfigWatcher` log an error (see
     * `OxfmtConfigWatcherTest`). The fixture deletes the files of light tests on tear down, so
     * delete them here and ignore that known error to keep it out of unrelated tests.
     */
    private fun deleteOxfmtJsonConfigFiles() {
        val root = myFixture.tempDirFixture.getFile("") ?: return
        val configs = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(root, null) {
            if (it.isOxfmtJsonConfigFile()) configs.add(it)
            true
        }
        if (configs.isEmpty()) return
        LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>,
                t: Throwable?): Set<Action> = emptySet()
        }) {
            WriteAction.runAndWait<Throwable> { configs.forEach { it.delete(this) } }
        }
    }
}
