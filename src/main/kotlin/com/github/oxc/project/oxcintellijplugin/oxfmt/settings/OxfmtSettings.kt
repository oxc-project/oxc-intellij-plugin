package com.github.oxc.project.oxcintellijplugin.oxfmt.settings

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtPackage.Companion.EMPTY_NODE_PACKAGE
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettingsState.Companion.DEFAULT_EXTENSION_LIST
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.lang.javascript.linter.JSNpmLinterState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import java.io.File

@Service(Service.Level.PROJECT)
@State(name = "OxfmtSettings", storages = [Storage("OxfmtSettings.xml")],
    category = SettingsCategory.TOOLS)
class OxfmtSettings(private val project: Project) : JSNpmLinterState<OxfmtSettings>,
    SimplePersistentStateComponent<OxfmtSettingsState>(OxfmtSettingsState()) {

    var configurationMode: ConfigurationMode
        get() = state.configurationMode
        set(value) {
            state.configurationMode = value
        }

    var supportedExtensions: MutableList<String>
        get() = state.supportedExtensions.takeIf { it.isNotEmpty() }
                ?: DEFAULT_EXTENSION_LIST.toMutableList()
        set(value) {
            state.supportedExtensions.clear()
            state.supportedExtensions.addAll(value)
        }

    var binaryPath: String
        get() = state.binaryPath ?: ""
        set(value) {
            state.binaryPath = value
        }

    var configPath: String
        get() = state.configPath ?: ""
        set(value) {
            val file = File(value)
            if (file.isFile) {
                state.configPath = file.path
                return
            }
            state.configPath = value
        }

    var fixAllOnSave: Boolean
        get() = isEnabled() && state.fixAllOnSave
        set(value) {
            state.fixAllOnSave = value
        }

    var preferOxfmtCodeStyleSettings: Boolean
        get() = state.preferOxfmtCodeStyleSettings
        set(value) {
            state.preferOxfmtCodeStyleSettings = value
        }

    fun isEnabled(): Boolean {
        return configurationMode !== ConfigurationMode.DISABLED
    }

    fun fileSupported(file: VirtualFile): Boolean {
        val fileExtension = file.extension
        return if (fileExtension != null) {
            supportedExtensions.contains(".$fileExtension")
        } else {
            false
        }
    }

    override fun getNodePackageRef(): NodePackageRef {
        return NodePackageRef.create(getPackage(null))
    }

    override fun withLinterPackage(
        nodePackageRef: NodePackageRef): OxfmtSettings {
        return this
    }

    fun getPackage(context: PsiElement?): NodePackage {
        return OxfmtPackage(project).getPackage(context?.containingFile?.virtualFile)
               ?: EMPTY_NODE_PACKAGE
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): OxfmtSettings = project.service()
    }
}
