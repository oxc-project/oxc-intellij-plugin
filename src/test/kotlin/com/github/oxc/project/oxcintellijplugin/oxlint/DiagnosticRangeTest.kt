package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.settings.OxlintSettings
import com.github.oxc.project.oxcintellijplugin.shared.ConfigurationMode
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.waitUntil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Uses a server with scripted answers. Needs Node on PATH. */
class DiagnosticRangeTest : OxcFixtureTestCase() {

  private lateinit var file: VirtualFile

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    val settings = OxlintSettings.getInstance(project)
    settings.state.configurationMode = ConfigurationMode.MANUAL
    settings.state.binaryPath = File("src/test/testData/oxlint/fix-all/fake-server.js").absolutePath
    file = myFixture.addFileToProject("index.js", TEXT).virtualFile
  }

  fun testRangeOverOnlyALineBreakIsShownAfterTheLine() {
    script(diagnostic("comma", 1, 6, 2, 0))

    val info = highlights().getValue("comma")

    assertEquals("comma's range", TextRange.from(endOf("  c: 2"), 0), info.range())
    assertTrue("comma is shown after the line", info.isAfterEndOfLine)
  }

  fun testRangeOverSeveralLineBreaksIsShownAfterItsFirstLine() {
    script(diagnostic("blank", 2, 2, 5, 0))

    val info = highlights().getValue("blank")

    assertEquals("blank's range", TextRange.from(endOf("};"), 0), info.range())
    assertTrue("blank is shown after the line", info.isAfterEndOfLine)
  }

  fun testRangeThatReachesCodeIsKept() {
    script(diagnostic("word", 6, 0, 6, 5), diagnostic("spanning", 5, 11, 6, 5))

    val infos = highlights(count = 2)

    val nextLine = TEXT.indexOf("const c")
    assertEquals("word's range", TextRange(nextLine, nextLine + 5), infos.getValue("word").range())
    assertEquals("spanning's range", TextRange(endOf("const b = 1"), nextLine + 5), infos.getValue("spanning").range())
    assertFalse("a range that reaches code isn't moved after the line", infos.values.any { it.isAfterEndOfLine })
  }

  /** The server's highlights, by message. */
  private fun highlights(count: Int = 1): Map<String, HighlightInfo> {
    myFixture.configureFromExistingVirtualFile(file)
    var infos = emptyMap<String, HighlightInfo>()
    waitUntil("the server's diagnostics are highlighted") {
      infos = myFixture.doHighlighting()
        .filter { it.description?.startsWith("oxc: ") == true }
        .associateBy { it.description!!.removePrefix("oxc: ").substringBefore(" ") }
      infos.size >= count
    }
    return infos
  }

  private fun HighlightInfo.range(): TextRange = TextRange(startOffset, endOffset)

  private fun endOf(line: String): Int = TEXT.indexOf(line) + line.length

  private fun diagnostic(message: String, startLine: Int, startCharacter: Int, endLine: Int, endCharacter: Int): String =
    """{ "start": [$startLine, $startCharacter], "end": [$endLine, $endCharacter], "message": "$message", "code": "test" }"""

  private fun script(vararg diagnostics: String) {
    Files.writeString(
      Path.of(file.parent.path).resolve("fake-server.json"),
      """{ "diagnostics": [${diagnostics.joinToString()}] }""",
    )
  }

  private companion object {
    const val TEXT = "const a = {\n  c: 2\n};\n\n\nconst b = 1\nconst c = 2;\n"
  }
}
