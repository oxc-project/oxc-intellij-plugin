package com.github.oxc.project.oxcintellijplugin.process

import com.github.oxc.project.oxcintellijplugin.backend.process.WslServerPathMapper
import com.github.oxc.project.oxcintellijplugin.testutil.assertDoesNotBlock
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.util.SystemInfo
import junit.framework.TestCase

/**
 * The mount root is deliberately not `/mnt/`, so these fail if a conversion gets it from the
 * distribution, which is what runs `wsl.exe`.
 */
class WslServerPathMapperTest : TestCase() {

  private val mapper = mapper(workingDirectory = "C:/project")

  fun testWindowsDriveIsUnderTheMountRoot() {
    assertEquals("/custom/c/Users/me/a.js", mapper.toServerPath("C:/Users/me/a.js"))
  }

  fun testMappedDriveIsUnderTheMountRoot() {
    assertEquals("/custom/z/a.js", mapper.toServerPath("Z:/a.js"))
  }

  fun testFileInTheDistributionUsesItsLinuxPath() {
    if (!SystemInfo.isWindows) return // The platform only recognizes WSL paths on Windows.
    assertEquals("/home/me/a.js", mapper.toServerPath("""\\wsl.localhost\Ubuntu\home\me\a.js"""))
    assertEquals("/home/me/a.js", mapper.toServerPath("""\\wsl$\Ubuntu\home\me\a.js"""))
  }

  fun testFileInAnotherDistributionCannotBeReached() {
    if (!SystemInfo.isWindows) return // The platform only recognizes WSL paths on Windows.
    assertNull(mapper.toServerPath("""\\wsl.localhost\Debian\home\me\a.js"""))
  }

  fun testNetworkShareCannotBeReached() {
    assertNull(mapper.toServerPath("""\\server\share\a.js"""))
  }

  fun testWindowsDriveFromTheServerIsMappedBack() {
    assertEquals("C:/Users/me/a.js", mapper.toLocalPath("/custom/c/Users/me/a.js")?.withForwardSlashes())
  }

  fun testLinuxPathFromTheServerUsesTheProjectsWslRoot() {
    if (!SystemInfo.isWindows) return // The platform only recognizes WSL paths on Windows.
    assertEquals(
      "//wsl$/Ubuntu/home/x/a.js",
      mapper(workingDirectory = """\\wsl$\Ubuntu\project""").toLocalPath("/home/x/a.js")?.withForwardSlashes(),
    )
    assertEquals(
      "//wsl.localhost/Ubuntu/home/x/a.js",
      mapper(workingDirectory = """\\wsl.localhost\Ubuntu\project""").toLocalPath("/home/x/a.js")?.withForwardSlashes(),
    )
  }

  fun testRelativePathFromTheServerHasNoLocalPath() {
    assertNull(mapper.toLocalPath("a.js"))
  }

  fun testUncPathFromTheServerHasNoLocalPath() {
    assertNull(mapper.toLocalPath("//wsl.localhost/Ubuntu/home/x/a.js"))
    assertNull(mapper.toLocalPath("//server/share/a.js"))
  }

  fun testConversionsDoNotBlock() {
    assertDoesNotBlock {
      mapper.toServerPath("C:/Users/me/a.js")
      mapper.toServerPath("""\\wsl.localhost\Ubuntu\home\me\a.js""")
      // A single-letter folder is what makes the platform look for a drive in a Linux path.
      mapper.toLocalPath("/custom/c/Users/me/a.js")
      mapper.toLocalPath("/home/x/a.js")
    }
  }

  // A local path uses the separators of the machine running the test.
  private fun String.withForwardSlashes() = replace('\\', '/')

  private fun mapper(workingDirectory: String) =
    WslServerPathMapper(WSLDistribution("Ubuntu"), "/custom/", workingDirectory)
}
