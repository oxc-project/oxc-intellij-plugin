package com.github.oxc.project.oxcintellijplugin.files

import com.github.oxc.project.oxcintellijplugin.backend.files.BackendFolderLister
import com.github.oxc.project.oxcintellijplugin.shared.files.BACKEND_ROOTS_PATH
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.ExperimentalPathApi

@OptIn(ExperimentalPathApi::class)
class BackendFolderListerTest : BasePlatformTestCase() {

  private lateinit var root: Path

  override fun setUp() {
    super.setUp()
    root = Files.createTempDirectory("oxc-lister").toRealPath()
    root.resolve("b.json").createFile()
    root.resolve("A.json").createFile()
    root.resolve("zFolder").createDirectory()
    root.resolve("aFolder").createDirectory()
  }

  override fun tearDown() {
    try {
      root.deleteRecursively()
    }
    finally {
      super.tearDown()
    }
  }

  fun testListsFoldersFirstAndSortsByName() {
    val folder = BackendFolderLister(null).list(root.toString())

    assertEquals(root.toString(), folder.path)
    assertEquals(root.parent.toString(), folder.parent)
    assertEquals(listOf("aFolder", "zFolder", "A.json", "b.json"), folder.entries.map { it.name })
    assertEquals(root.resolve("A.json").toString(), folder.entries[2].path)
    assertNull(folder.error)
  }

  fun testBlankPathUsesTheDefaultFolder() {
    assertEquals(root.toString(), BackendFolderLister(root.toString()).list(null).path)
    assertEquals(root.toString(), BackendFolderLister(root.toString()).list("  ").path)
  }

  fun testAFileListsItsFolderWithoutAnError() {
    val folder = BackendFolderLister(null).list(root.resolve("b.json").toString())

    assertEquals(root.toString(), folder.path)
    assertNull(folder.error)
  }

  fun testAMissingPathListsTheClosestFolderWithAnError() {
    val folder = BackendFolderLister(null).list(root.resolve("missing/deeper.json").toString())

    assertEquals(root.toString(), folder.path)
    assertNotNull(folder.error)
  }

  fun testARelativePathIsUnderTheDefaultFolder() {
    root.resolve("configs").createDirectory()
    root.resolve("configs/team.json").createFile()

    val folder = BackendFolderLister(root.toString()).list("configs/team.json")

    assertEquals(root.resolve("configs").toString(), folder.path)
    assertEquals(listOf("team.json"), folder.entries.map { it.name })
    assertNull(folder.error)
  }

  fun testAParentRelativePathIsUnderTheDefaultFolder() {
    val folder = BackendFolderLister(root.resolve("zFolder").toString()).list("../b.json")

    assertEquals(root.toString(), folder.path)
    assertNull(folder.error)
  }

  fun testAnAbsolutePathIgnoresTheDefaultFolder() {
    val folder = BackendFolderLister(root.resolve("zFolder").toString()).list(root.resolve("b.json").toString())

    assertEquals(root.toString(), folder.path)
    assertNull(folder.error)
  }

  fun testRootsListHasNoParent() {
    val roots = BackendFolderLister(null).list(BACKEND_ROOTS_PATH)

    assertEquals(BACKEND_ROOTS_PATH, roots.path)
    assertNull(roots.parent)
    assertNotEmpty(roots.entries)
    assertTrue(roots.entries.all { it.isDirectory })
  }
}
