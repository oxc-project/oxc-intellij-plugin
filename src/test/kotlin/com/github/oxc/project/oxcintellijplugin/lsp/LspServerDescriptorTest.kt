package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspFormattingSupport
import com.github.oxc.project.oxcintellijplugin.oxfmt.lsp.OxfmtLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintFixKind
import com.github.oxc.project.oxcintellijplugin.oxlint.OxlintUnusedDisableDirectivesSeverity
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspDiagnosticsSupport
import com.github.oxc.project.oxcintellijplugin.oxlint.lsp.OxlintLspServerDescriptor
import com.github.oxc.project.oxcintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCodeActionsDisabled
import com.intellij.platform.lsp.api.customization.LspCompletionDisabled
import com.intellij.platform.lsp.api.customization.LspDiagnosticsDisabled
import com.intellij.platform.lsp.api.customization.LspFormattingDisabled
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionDisabled
import com.intellij.platform.lsp.api.customization.LspHoverDisabled
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import java.nio.file.Files
import java.nio.file.Path
import org.eclipse.lsp4j.ConfigurationItem

/**
 * What the plugin sends to the language servers. The servers are never started here; MANUAL mode
 * keeps the descriptors from needing a Node.js interpreter.
 */
class LspServerDescriptorTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        OxlintSettings.getInstance(project).configurationMode = ConfigurationMode.MANUAL
        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.MANUAL
    }

    fun testOxlintDefaultWorkspaceOptions() {
        OxlintSettings.getInstance(project).configPath = ""

        assertEquals(
            mapOf(
                "configPath" to "",
                "disableNestedConfig" to false,
                "fixKind" to "safe_fix",
                "flags" to mapOf("disable_nested_config" to "false", "fix_kind" to "safe_fix"),
                "typeAware" to false,
                "unusedDisableDirectives" to "allow",
            ),
            options(oxlint()))
    }

    fun testOxlintWorkspaceOptionsFollowTheSettings() {
        val settings = OxlintSettings.getInstance(project)
        settings.configPath = "/project/custom.json"
        settings.disableNestedConfig = true
        settings.fixKind = OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION
        settings.typeAware = true
        settings.unusedDisableDirectivesSeverity = OxlintUnusedDisableDirectivesSeverity.DENY

        assertEquals(
            mapOf(
                "configPath" to "/project/custom.json",
                "disableNestedConfig" to true,
                "fixKind" to "dangerous_fix_or_suggestion",
                "flags" to mapOf("disable_nested_config" to "true", "fix_kind" to "dangerous_fix_or_suggestion"),
                "typeAware" to true,
                "unusedDisableDirectives" to "deny",
            ),
            options(oxlint()))
    }

    fun testOxlintConfigPathIsOnlySentInManualMode() {
        val settings = OxlintSettings.getInstance(project)
        settings.configPath = "/project/custom.json"
        val descriptor = oxlint()

        settings.configurationMode = ConfigurationMode.AUTOMATIC
        assertNull(options(descriptor)["configPath"])
    }

    fun testOxlintClientCapabilitiesEnablePullDiagnostics() {
        val capabilities = oxlint().clientCapabilities

        assertTrue(capabilities.workspace.configuration)
        assertTrue("refreshSupport switches the server to pull diagnostics", capabilities.workspace.diagnostics.refreshSupport)
    }

    fun testOxlintOnlyProvidesDiagnosticsAndCodeActions() {
        val customization = oxlint().lspCustomization

        assertInstanceOf(customization.diagnosticsCustomizer, OxlintLspDiagnosticsSupport::class.java)
        assertFalse(customization.codeActionsCustomizer is LspCodeActionsDisabled)
        assertSame(LspFormattingDisabled, customization.formattingCustomizer)
        assertSame(LspCompletionDisabled, customization.completionCustomizer)
        assertSame(LspHoverDisabled, customization.hoverCustomizer)
        assertSame(LspGoToDefinitionDisabled, customization.goToDefinitionCustomizer)
    }

    fun testOxfmtWorkspaceOptions() {
        OxfmtSettings.getInstance(project).configPath = "/project/.oxfmtrc.json"
        OxfmtSettings.getInstance(project).disableNestedConfig = true

        val options = options(oxfmt())

        assertEquals("/project/.oxfmtrc.json", options["fmt.configPath"])
        assertEquals(true, options["fmt.disableNestedConfig"])
        assertEquals(true, options["fmt.experimental"])
    }

    fun testOxfmtConfigPathIsOnlySentInManualMode() {
        OxfmtSettings.getInstance(project).configPath = "/project/.oxfmtrc.json"
        val descriptor = oxfmt()

        OxfmtSettings.getInstance(project).configurationMode = ConfigurationMode.AUTOMATIC
        assertNull(options(descriptor)["fmt.configPath"])
    }

    /**
     * QUESTIONABLE: the Oxfmt server is sent Oxlint options (`run`, `typeAware`,
     * `unusedDisableDirectives`, `flags`, `configPath`), apparently left over from when one server
     * did both. `unusedDisableDirectives` is even a boolean here while Oxlint expects
     * "allow"/"warn"/"deny".
     */
    fun testOxfmtReceivesLeftoverOxlintOptions() {
        val options = options(oxfmt())

        assertEquals("onSave", options["run"])
        assertEquals(false, options["typeAware"])
        assertEquals(false, options["unusedDisableDirectives"])
        assertEquals(emptyMap<String, Any?>(), options["flags"])
        assertTrue(options.containsKey("configPath"))
        assertNull(options["configPath"])
    }

    fun testOxfmtOnlyProvidesFormatting() {
        val customization = oxfmt().lspCustomization

        assertInstanceOf(customization.formattingCustomizer, OxfmtLspFormattingSupport::class.java)
        assertSame(LspDiagnosticsDisabled, customization.diagnosticsCustomizer)
        assertSame(LspCodeActionsDisabled, customization.codeActionsCustomizer)
        assertSame(LspCompletionDisabled, customization.completionCustomizer)
    }

    fun testOxfmtClientCapabilitiesEnableWorkspaceConfiguration() {
        assertTrue(oxfmt().clientCapabilities.workspace.configuration)
    }

    fun testUnknownWorkspaceScopeHasNoConfiguration() {
        val item = ConfigurationItem().apply { scopeUri = "file:///somewhere/else" }

        assertNull(oxlint().getWorkspaceConfiguration(item))
        assertNull(oxfmt().getWorkspaceConfiguration(item))
    }

    fun testSupportedFilesMustBeUnderTheRootAndHaveASupportedExtension() {
        val inside = createFile("app/index.ts")
        val unsupported = createFile("app/notes.txt")
        val outside = createFile("other/index.ts")
        val root = inside.parent

        val oxlint = OxlintLspServerDescriptor(project, root, executable(), emptyList())
        assertTrue(oxlint.isSupportedFile(inside))
        assertFalse(oxlint.isSupportedFile(unsupported))
        assertFalse(oxlint.isSupportedFile(outside))

        val oxfmt = OxfmtLspServerDescriptor(project, root, executable(), emptyList())
        assertTrue(oxfmt.isSupportedFile(inside))
        assertFalse(oxfmt.isSupportedFile(unsupported))
        assertFalse(oxfmt.isSupportedFile(outside))
    }

    fun testServerNamesAndRoots() {
        assertEquals("Oxlint", oxlint().presentableName)
        assertEquals("Oxfmt", oxfmt().presentableName)
        assertEquals(listOf(root()), oxlint().roots.toList())
    }

    @Suppress("UNCHECKED_CAST")
    private fun options(descriptor: LspServerDescriptor): Map<String, Any?> {
        val initOptions = descriptor.createInitializationOptions() as List<Map<String, Any?>>
        return initOptions.single()["options"] as Map<String, Any?>
    }

    private fun oxlint() = OxlintLspServerDescriptor(project, root(), executable(), emptyList())

    private fun oxfmt() = OxfmtLspServerDescriptor(project, root(), executable(), emptyList())

    private fun root(): VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.tempDirPath)!!

    private fun executable(): String = "${myFixture.tempDirPath}/oxc-language-server"

    private fun createFile(relativePath: String): VirtualFile {
        val path = Path.of(myFixture.tempDirPath, relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, "")
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    }
}
