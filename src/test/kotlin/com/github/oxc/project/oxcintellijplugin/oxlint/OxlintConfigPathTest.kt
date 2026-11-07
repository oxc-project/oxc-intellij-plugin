package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintConfigPath
import com.github.oxc.project.oxcintellijplugin.backend.process.WslServerPathMapper
import com.intellij.execution.wsl.WSLDistribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plain unit tests, so the Windows and WSL cases run on every OS. */
class OxlintConfigPathTest {

  private fun local(configured: String?, workspace: String = "/work"): OxlintConfigPath? =
    OxlintConfigPath.of(configured, workspace, { it }, { it })

  private fun wsl(configured: String, workspace: String = "C:/work"): OxlintConfigPath? =
    OxlintConfigPath.of(
      configured,
      workspace,
      { path -> "/mnt/c/" + path.removePrefix("C:/") },
      { path ->
        when {
          path.startsWith("/mnt/c/") -> "C:/" + path.removePrefix("/mnt/c/")
          path.startsWith("//") -> null
          path.startsWith("/") -> "//wsl.localhost/Ubuntu$path"
          else -> null
        }
      },
    )

  @Test
  fun blankIsNoConfig() {
    assertNull(local(null))
    assertNull(local("   "))
  }

  @Test
  fun relativeInsideStaysRelative() {
    assertEquals(OxlintConfigPath("configs/a.json", null), local("configs/a.json"))
    assertEquals(OxlintConfigPath("a.json", null), local("./configs/../a.json"))
  }

  @Test
  fun absoluteInsideBecomesRelative() {
    assertEquals(OxlintConfigPath("configs/a.json", null), local("/work/configs/a.json"))
  }

  @Test
  fun aFolderWithTheSamePrefixIsOutside() {
    assertEquals(OxlintConfigPath("/work2/a.json", "/work2/a.json"), local("/work2/a.json"))
  }

  @Test
  fun absoluteOutsideIsWatched() {
    assertEquals(OxlintConfigPath("/other/a.json", "/other/a.json"), local("/other/a.json"))
  }

  @Test
  fun relativeOutsideIsSentAsIsAndWatched() {
    assertEquals(OxlintConfigPath("../other/a.json", "/other/a.json"), local("../other/a.json"))
  }

  @Test
  fun windowsPathInsideTheWorkspaceWithAWslServerIsRelative() {
    assertEquals(OxlintConfigPath("configs/a.json", null), wsl("C:\\work\\configs\\a.json"))
    assertEquals(OxlintConfigPath("configs/a.json", null), wsl("C:/work/configs/a.json"))
  }

  @Test
  fun windowsPathOutsideTheWorkspaceIsConvertedForAWslServer() {
    assertEquals(OxlintConfigPath("/mnt/c/other/a.json", "C:/other/a.json"), wsl("C:\\other\\a.json"))
  }

  @Test
  fun wslPathInsideTheWorkspaceIsRecognized() {
    assertEquals(OxlintConfigPath("a.json", null), wsl("/mnt/c/work/a.json"))
  }

  @Test
  fun wslPathInAWslProjectIsRelative() {
    assertEquals(
      OxlintConfigPath("a.json", null),
      wsl("/home/me/work/a.json", workspace = "//wsl.localhost/Ubuntu/home/me/work"),
    )
  }

  @Test
  fun uncPathInAWslProjectIsRelativeWithTheRealMapper() {
    val workspace = "//wsl.localhost/Ubuntu/home/me/work"
    val mapper = WslServerPathMapper(WSLDistribution("Ubuntu"), "/mnt/", workspace)

    for (configured in listOf("$workspace/configs/a.json", "\\\\wsl.localhost\\Ubuntu\\home\\me\\work\\configs\\a.json")) {
      assertEquals(
        OxlintConfigPath("configs/a.json", null),
        OxlintConfigPath.of(configured, workspace, mapper::toServerPath, mapper::toLocalPath),
      )
    }
  }

  @Test
  fun uncPathOutsideAWslProjectIsWatchedAsWritten() {
    val workspace = "//wsl.localhost/Ubuntu/home/me/work"
    val outside = "//wsl.localhost/Ubuntu/home/me/other/a.json"
    val mapper = WslServerPathMapper(WSLDistribution("Ubuntu"), "/mnt/", workspace)

    assertEquals(
      outside,
      OxlintConfigPath.of(outside, workspace, mapper::toServerPath, mapper::toLocalPath)?.fileOutsideWorkspace,
    )
  }
}
