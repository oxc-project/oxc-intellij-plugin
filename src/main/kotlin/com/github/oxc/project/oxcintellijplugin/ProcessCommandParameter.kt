package com.github.oxc.project.oxcintellijplugin

import java.nio.file.Path

sealed interface ProcessCommandParameter {
    class Value(val value: String) : ProcessCommandParameter {
        override fun toString() = value
    }

    class FilePath(val path: Path) : ProcessCommandParameter {
        override fun toString() = path.toString()
    }
}
