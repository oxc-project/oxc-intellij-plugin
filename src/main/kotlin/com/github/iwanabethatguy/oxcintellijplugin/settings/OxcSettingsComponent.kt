package com.github.iwanabethatguy.oxcintellijplugin.settings

import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent
import javax.swing.JPanel


class OxcSettingsComponent {
    private var mainPanel: JPanel? = null
//    private val runWhenOnSave = JBRadioButton("onSave")
//    private val runWhenOnType = jBGROUP("onType")
    private val enableCheckBox = JBCheckBox("Enable oxc language server: ")

    fun AppSettingsComponent() {
        mainPanel = FormBuilder.createFormBuilder()
                .addComponent(enableCheckBox)
                .addComponentFillVertically(JPanel(), 0)
                .getPanel()
    }

    fun getPanel(): JPanel? {
        return mainPanel
    }


    @NotNull
    fun getEnable(): Boolean {
        return enableCheckBox.isEnabled
    }

    fun setEnable(@NotNull newValue: Boolean) {
        enableCheckBox.isEnabled = newValue
    }

}