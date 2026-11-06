package com.github.oxc.project.oxcintellijplugin.windows

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.lsp.OxlintLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting.onPooledThread
import com.github.oxc.project.oxcintellijplugin.testutil.assertDoesNotBlock
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * The server runs inside WSL, so it has to be given WSL paths, whether the project is on the Windows
 * side (`/mnt/...`) or stored inside WSL (`/tmp/...`). The test itself runs on the UI thread, where
 * the IDE doesn't allow `wsl.exe` to run. See [WslTesting] for how to enable this test.
 */
class WslWorkspaceUriTest : OxcFixtureTestCase() {

  override fun shouldRunTest(): Boolean = WslTesting.enabled && super.shouldRunTest()

  fun testServerInWslIsGivenWslUris() {
    val root = LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.tempDirPath)!!
    val descriptor = OxlintLspClientDescriptor(project, root, "${myFixture.tempDirPath}/oxlint")
    val expectedPath = WslPath.parseWindowsUncPath(root.path)?.linuxPath
                       ?: "/mnt/${root.path[0].lowercaseChar()}${root.path.substring(2)}"

    val process = onPooledThread { descriptor.startServerProcess() }
    process.startNotify()
    try {
      val uri = assertDoesNotBlock { descriptor.getFileUri(root) }

      assertEquals("file://$expectedPath", uri)
      assertEquals(root, descriptor.findFileByUri(uri))
      assertEquals(expectedPath, descriptor.createInitializeParams().rootPath)
    }
    finally {
      process.destroyProcess()
      process.waitFor(10_000)
    }
  }
}
