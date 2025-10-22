package com.github.oxc.project.oxcintellijplugin.settings

import com.intellij.util.ui.ColumnInfo

data class Flag(var key: String, var value: String)

abstract class EditableColumn<Item, Aspect>(private val name: String) :
    ColumnInfo<Item, Aspect>(name) {

    override fun isCellEditable(item: Item?): Boolean {
        return true
    }
}

fun createFlagKeyColumn(): ColumnInfo<Flag, String> {
    return object : EditableColumn<Flag, String>("Key") {
        override fun setValue(item: Flag?, value: String?) {
            if (item != null && value != null) {
                item.key = value
            }
        }

        override fun valueOf(item: Flag?): String? {
            return item?.key
        }
    }
}

fun createFlagValueColumn(): ColumnInfo<Flag, String> {
    return object : EditableColumn<Flag, String>("Value") {
        override fun setValue(item: Flag?, value: String?) {
            if (item != null && value != null) {
                item.value = value
            }
        }

        override fun valueOf(item: Flag?): String? {
            return item?.value
        }
    }
}
