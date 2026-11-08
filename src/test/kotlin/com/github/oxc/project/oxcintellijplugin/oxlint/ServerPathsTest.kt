package com.github.oxc.project.oxcintellijplugin.oxlint

import com.github.oxc.project.oxcintellijplugin.backend.oxlint.OxlintDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ServerLaunch
import com.github.oxc.project.oxcintellijplugin.backend.lsp.pathToFileUri
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolLspClientDescriptor
import com.github.oxc.project.oxcintellijplugin.testutil.OxcFixtureTestCase
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting
import com.github.oxc.project.oxcintellijplugin.testutil.WslTesting.onPooledThread
import com.github.oxc.project.oxcintellijplugin.testutil.assertDoesNotBlock
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClientDescriptor

class ServerPathsTest : OxcFixtureTestCase() {

  /**
   * Creating the run needs an interpreter, so this also fails if it's created by asking for a URI,
   * because that can block. Until the server starts there's nothing to map with, so the URI is the
   * platform's, which is the Linux path for a project in WSL.
   */
  fun testFileUriBeforeTheServerStartsNeedsNothingFromTheInterpreter() {
    NodeJsInterpreterManager.getInstance(project)
      .setInterpreterRef(NodeJsInterpreterRef.create(""), testRootDisposable)
    assertNull(NodeJsInterpreterManager.getInstance(project).interpreter)
    val root = root()
    val descriptor = descriptor(root)

    val uri = assertDoesNotBlock { descriptor.getFileUri(root) }

    assertEquals(platformDescriptor(root).getFileUri(root), uri)
  }

  fun testFileUriAfterTheServerStartsDoesNotBlock() {
    if (WslTesting.enabled) return // WslWorkspaceUriTest expects a different URI.
    val root = root()
    val descriptor = descriptor(root)

    val process = onPooledThread { descriptor.startServerProcess() }
    process.startNotify()
    try {
      val uri = assertDoesNotBlock { descriptor.getFileUri(root) }

      assertEquals(pathToFileUri(root.path), uri)
      assertEquals(root, descriptor.findFileByUri(uri))
      assertEquals(root.path, descriptor.createInitializeParams().rootPath)
    }
    finally {
      process.destroyProcess()
      process.waitFor(10_000)
    }
  }

  /** Oxlint matches the URIs that it gets, so ours have to be spelled as the platform spells them. */
  fun testUriIsSpelledAsThePlatformSpellsIt() {
    if (WslTesting.enabled) return // The platform's URI is the wrong one for a server in WSL.
    val folder = myFixture.tempDirFixture.findOrCreateDir("a b#c+é%20(d)")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(
      "${myFixture.tempDirPath}/${folder.name}")!!

    assertEquals(platformDescriptor(file).getFileUri(file), pathToFileUri(file.path))
  }

  /** The platform's own URIs, which convert a path in WSL or a container through Eel. */
  private fun platformDescriptor(root: VirtualFile) = object : LspClientDescriptor(project, "Platform", root) {
    override fun isSupportedFile(file: VirtualFile): Boolean = true
  }

  private fun root(): VirtualFile =
    LocalFileSystem.getInstance().refreshAndFindFileByPath(myFixture.tempDirPath)!!

  private fun descriptor(root: VirtualFile) =
    ToolLspClientDescriptor(project, OxlintDefinition, ServerLaunch("${myFixture.tempDirPath}/oxlint", listOf("--lsp"), root))
}
