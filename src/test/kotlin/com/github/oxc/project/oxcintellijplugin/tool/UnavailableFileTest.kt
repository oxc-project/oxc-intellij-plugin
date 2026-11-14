package com.github.oxc.project.oxcintellijplugin.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcFixAllResult
import com.github.oxc.project.oxcintellijplugin.shared.OxcTool
import com.github.oxc.project.oxcintellijplugin.shared.OxcToolApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtCodeStyleApi
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import fleet.util.UID
import kotlinx.coroutines.runBlocking

/**
 * A file or project the backend can't resolve gets its own answer, instead of "nothing to fix" or "no
 * config", which would tell the user something untrue about the file.
 */
class UnavailableFileTest : BasePlatformTestCase() {

  fun testFixAllForADeletedFileSaysSo() {
    val id = deletedFile()

    assertEquals(OxcFixAllResult.FileUnavailable, onPooledThread {
      OxcToolApi.getInstance().startServerForFixAll(project.projectId(), OxcTool.OXLINT, id)
    })
    assertEquals(OxcFixAllResult.FileUnavailable, onPooledThread {
      OxcToolApi.getInstance().fixAll(project.projectId(), OxcTool.OXLINT, id)
    })
  }

  fun testFixAllInAProjectTheBackendCannotFindSaysSo() {
    val file = myFixture.addFileToProject("a.js", "").virtualFile.rpcId()

    assertEquals(OxcFixAllResult.FileUnavailable, onPooledThread {
      OxcToolApi.getInstance().fixAll(ProjectId(UID.random()), OxcTool.OXLINT, file)
    })
  }

  fun testImportForAFileTheBackendCannotFindSaysSo() {
    val deleted = deletedFile()
    assertEquals(OxfmtImportResult.FILE_UNAVAILABLE, onPooledThread {
      OxfmtCodeStyleApi.getInstance().importCodeStyle(project.projectId(), deleted)
    })
    val file = myFixture.addFileToProject("b.js", "").virtualFile.rpcId()
    assertEquals(OxfmtImportResult.FILE_UNAVAILABLE, onPooledThread {
      OxfmtCodeStyleApi.getInstance().importCodeStyle(ProjectId(UID.random()), file)
    })
  }

  /** In a monolith a deleted file's id still finds the file, which is no longer valid. */
  private fun deletedFile(): VirtualFileId {
    val file = myFixture.addFileToProject("gone.js", "").virtualFile
    val id = file.rpcId()
    WriteAction.runAndWait<Throwable> { file.delete(this) }
    return id
  }

  private fun <T> onPooledThread(call: suspend () -> T): T =
    PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread<T> { runBlocking { call() } }, 30_000)
}
