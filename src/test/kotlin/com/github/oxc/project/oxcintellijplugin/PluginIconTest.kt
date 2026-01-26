package com.github.oxc.project.oxcintellijplugin

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginIconTest {

    @Test
    fun iconSvgFile_svgElementDoesNotContainDimensions() {
        // https://github.com/oxc-project/oxc-intellij-plugin/issues/379
        // https://github.com/oxc-project/oxc-intellij-plugin/issues/142
        val svgElementContents = getSvgElementContents(Path.of("src", "main", "resources", "icons", "oxcRound.svg"))

        assertFalse(svgElementContents.contains("height"))
        assertFalse(svgElementContents.contains("width"))
    }

    @Test
    fun logoSvgFile_svgElementDoesNotContainDimensions() {
        // https://github.com/oxc-project/oxc-intellij-plugin/issues/379
        // https://github.com/oxc-project/oxc-intellij-plugin/issues/142
        val svgElementContents = getSvgElementContents(Path.of("src", "main", "resources", "META-INF", "pluginIcon.svg"))

        assertFalse(svgElementContents.contains("height"))
        assertFalse(svgElementContents.contains("width"))
    }

    fun getSvgElementContents(svgPath: Path): String {
        val svgContents = Files.readString(svgPath)
        val svgElementStart = svgContents.indexOf("<svg")
        val svgElementEnd = svgContents.indexOf(">", svgElementStart)
        return svgContents.substring(svgElementStart, svgElementEnd).lowercase()
    }

}
