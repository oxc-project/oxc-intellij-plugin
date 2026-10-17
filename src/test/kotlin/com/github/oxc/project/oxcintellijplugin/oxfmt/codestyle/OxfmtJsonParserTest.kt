package com.github.oxc.project.oxcintellijplugin.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig
import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtConfig.TrailingCommaOption
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.openapi.components.service

class OxfmtJsonParserTest : OxcLightTestCase() {

    fun testEmptyConfigUsesDefaults() {
        assertEquals(OxfmtConfig.DEFAULT, parse("{}"))
    }

    fun testReadsEverySupportedOption() {
        val config = parse("""
            {
              "bracketSameLine": true,
              "bracketSpacing": false,
              "endOfLine": "crlf",
              "printWidth": 120,
              "semi": false,
              "singleQuote": true,
              "tabWidth": 4,
              "trailingComma": "es5",
              "useTabs": true
            }
        """.trimIndent())

        assertEquals(OxfmtConfig(bracketSameLine = true, bracketSpacing = false, lineSeparator = "crlf",
            printWidth = 120, semi = false, singleQuote = true, tabWidth = 4,
            trailingComma = TrailingCommaOption.es5, useTabs = true), config)
    }

    fun testNullValuesUseDefaults() {
        assertEquals(OxfmtConfig.DEFAULT, parse("""{ "semi": null, "tabWidth": null, "trailingComma": null }"""))
    }

    /**
     * QUESTIONABLE: a value of the wrong type (`"semi": "no"`) throws `ClassCastException` instead
     * of falling back to the default. `readProperty` tries to catch the cast failure, but the cast
     * is to an erased type parameter, so it succeeds there and fails later at the call site. The
     * exception escapes from the code style importer, the config watcher and the code style
     * modifier.
     */
    fun testValueOfTheWrongTypeThrows() {
        assertThrows(ClassCastException::class.java) {
            parse("""{ "semi": "no" }""")
        }
    }

    fun testNonObjectTopLevelValueUsesDefaults() {
        assertEquals(OxfmtConfig.DEFAULT, parse("[]"))
    }

    fun testUnknownAndUnrelatedOptionsAreIgnored() {
        assertEquals(OxfmtConfig.DEFAULT.copy(singleQuote = true),
            parse("""{ "${'$'}schema": "./schema.json", "ignorePatterns": ["dist"], "singleQuote": true }"""))
    }

    fun testJsoncCommentsAreSupported() {
        val config = parse("""
            {
              // Prefer single quotes
              "singleQuote": true,
            }
        """.trimIndent(), ".oxfmtrc.jsonc")

        assertTrue(config.singleQuote)
    }

    fun testNonJsonFileUsesDefaults() {
        val file = addFile("oxfmt.config.ts", "export default { singleQuote: true }")

        assertEquals(OxfmtConfig.DEFAULT, project.service<OxfmtJsonParser>().parseConfigFile(file))
    }

    /**
     * QUESTIONABLE: an unrecognized `trailingComma` value (a typo, or a different letter case)
     * throws instead of falling back to the default like every other invalid value. The exception
     * escapes from the code style importer, the config watcher and the code style modifier.
     */
    fun testUnknownTrailingCommaValueThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{ "trailingComma": "ES5" }""")
        }
    }

    private fun parse(text: String, name: String = ".oxfmtrc.json"): OxfmtConfig {
        val file = addFile(name, text)
        return project.service<OxfmtJsonParser>().parseConfigFile(file)
    }
}
