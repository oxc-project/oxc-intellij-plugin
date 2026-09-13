package com.github.oxc.project.oxcintellijplugin

enum class BinarySource(private val label: String) {
    AUTO("Automatic"),
    VITE_PLUS("Vite+"),
    OXC("Standalone Oxc");

    override fun toString(): String = label
}
