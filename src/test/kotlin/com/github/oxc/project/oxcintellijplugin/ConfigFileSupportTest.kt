package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.oxfmt.OxfmtSchemaProviderFactory
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintSchemaProviderFactory
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.testutil.OxcLightTestCase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.SchemaType

/** File icons and JSON schema validation for Oxlint and Oxfmt configuration files. */
class ConfigFileSupportTest : OxcLightTestCase() {

    fun testConfigFilesGetTheOxcIcon() {
        for (name in listOf(".oxlintrc.json", ".oxlintrc.jsonc", "oxlint.config.ts", ".oxfmtrc.json",
            ".oxfmtrc.jsonc", "oxfmt.config.ts")) {
            assertSame(name, OxcIcons.OxcRound, icon(file(name)))
        }
    }

    fun testOtherFilesKeepTheirIcon() {
        assertNull(icon(file("package.json")))
        assertNull(icon(file("index.js")))
        assertNull(OxcIconProvider().getIcon(PsiManager.getInstance(project).findDirectory(file("dir/x.js").parent)!!, 0))
    }

    fun testCustomOxlintConfigPathGetsTheOxcIcon() {
        val custom = file("custom-oxlint.json")
        OxlintSettings.getInstance(project).state.configPath = custom.path

        assertSame(OxcIcons.OxcRound, icon(custom))
    }

    /**
     * QUESTIONABLE: a custom Oxfmt config path does not get the Oxc icon, because the icon provider
     * only checks the Oxlint setting. It does get the Oxfmt schema
     * ([testCustomOxfmtConfigPathGetsTheSchema]) and a custom Oxlint config gets both.
     */
    fun testCustomOxfmtConfigPathDoesNotGetTheOxcIcon() {
        val custom = file("custom-oxfmt.json")
        OxfmtSettings.getInstance(project).state.configPath = custom.path

        assertNull(icon(custom))
    }

    fun testOxlintSchemaAppliesToOxlintJsonConfigs() {
        val provider = oxlintSchema()

        assertTrue(provider.isAvailable(file(".oxlintrc.json")))
        assertTrue(provider.isAvailable(file(".oxlintrc.jsonc")))
        assertFalse(provider.isAvailable(file(".oxfmtrc.json")))
        assertFalse(provider.isAvailable(file("package.json")))
        assertEquals("Oxlint", provider.name)
        assertEquals(SchemaType.remoteSchema, provider.schemaType)
    }

    fun testOxfmtSchemaAppliesToOxfmtJsonConfigs() {
        val provider = oxfmtSchema()

        assertTrue(provider.isAvailable(file(".oxfmtrc.json")))
        assertTrue(provider.isAvailable(file(".oxfmtrc.jsonc")))
        assertFalse(provider.isAvailable(file(".oxlintrc.json")))
        assertEquals("Oxfmt", provider.name)
        assertEquals(SchemaType.remoteSchema, provider.schemaType)
    }

    fun testCustomOxlintConfigPathGetsTheSchema() {
        val custom = file("custom-oxlint.json")
        OxlintSettings.getInstance(project).state.configPath = custom.path

        assertTrue(oxlintSchema().isAvailable(custom))
    }

    fun testCustomOxfmtConfigPathGetsTheSchema() {
        val custom = file("custom-oxfmt.json")
        OxfmtSettings.getInstance(project).state.configPath = custom.path

        assertTrue(oxfmtSchema().isAvailable(custom))
    }

    /**
     * QUESTIONABLE: the schemas are always downloaded from the `main` branch of the oxc repository,
     * not from the installed `oxlint`/`oxfmt` package (which ships `configuration_schema.json`).
     * Projects on an older version get validated against options their version does not have, and
     * validation needs network access.
     */
    fun testSchemasComeFromTheOxcMainBranch() {
        assertEquals("https://raw.githubusercontent.com/oxc-project/oxc/main/npm/oxlint/configuration_schema.json",
            schemaUrl(OxlintSchemaProviderFactory()))
        assertEquals("https://raw.githubusercontent.com/oxc-project/oxc/main/npm/oxfmt/configuration_schema.json",
            schemaUrl(OxfmtSchemaProviderFactory()))
    }

    private fun schemaUrl(factory: com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory): String {
        // getSchemaFile() resolves the URL to a (lazily downloaded) virtual file that keeps the address.
        return factory.getProviders(project).single()!!.schemaFile!!.url
    }

    private fun oxlintSchema(): JsonSchemaFileProvider = OxlintSchemaProviderFactory().getProviders(project).single()!!

    private fun oxfmtSchema(): JsonSchemaFileProvider = OxfmtSchemaProviderFactory().getProviders(project).single()!!

    private fun icon(file: VirtualFile) = OxcIconProvider().getIcon(PsiManager.getInstance(project).findFile(file)!!, 0)

    private fun file(path: String): VirtualFile =
        myFixture.findFileInTempDir(path) ?: myFixture.addFileToProject(path, "{}").virtualFile
}
