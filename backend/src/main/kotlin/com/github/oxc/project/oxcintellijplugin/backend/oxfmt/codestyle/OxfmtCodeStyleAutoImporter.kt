package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.oxfmt.settings.OxfmtSettings
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtConfigNotice
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtImportResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/**
 * With "Prefer Oxfmt configuration to IDE code style" on, imports the config that the project's folder uses
 * into the project's own code style when that config changes, as Import does, and tells the frontend when
 * that changed anything. Main did this for `.oxfmtrc.json`; this follows the same lookup as Import, so it
 * covers every config the server reads, including a manual config and, under `vp`, `vite.config.*`.
 *
 * A nested config only changes the overlay of the files below it, so changing one imports nothing. Neither
 * does removing the project's config, which leaves the code style as it was.
 */
@Service(Service.Level.PROJECT)
class OxfmtCodeStyleAutoImporter(private val project: Project, private val scope: CoroutineScope) {

  /**
   * The last import asked for. Each waits for the one before, so that saving twice quickly imports once and
   * then finds it already imported.
   */
  private var last: Job? = null

  @Volatile
  private var disabledForTests = false

  /**
   * [paths] are the configs that changed, by their old and new paths. [moved] is whether one was deleted,
   * renamed or moved, which can change which config the project uses without changing that config.
   */
  fun configsChanged(paths: Collection<String>, moved: Boolean) {
    if (disabledForTests) return
    synchronized(this) {
      val previous = last
      last = scope.launch {
        previous?.join()
        importIfChanged(paths, moved)
      }
    }
  }

  /** Keeps a test that creates configs from racing with what Import's own tests check, until [disposable] goes. */
  @TestOnly
  fun setEnabledForTests(enabled: Boolean, disposable: Disposable) {
    disabledForTests = !enabled
    Disposer.register(disposable) { disabledForTests = false }
  }

  @TestOnly
  suspend fun awaitImports() {
    synchronized(this) { last }?.join()
  }

  private suspend fun importIfChanged(paths: Collection<String>, moved: Boolean) {
    val location = readAction {
      if (project.isDisposed || !OxfmtSettings.getInstance(project).state.preferOxfmtCodeStyleSettings) return@readAction null
      projectFolder()?.let { locateOxfmtConfig(project, it) }
    } ?: return
    val configs = listOf(location.file) + location.fallbacks
    if (!moved && configs.none { config -> paths.any { FileUtil.pathsEqual(it, config.path) } }) return

    val import = importOxfmtCodeStyle(project, location, asked = false)
    val source = import.source
    if (import.result == OxfmtImportResult.IMPORTED && source != null) {
      OxfmtJsConfigLoader.getInstance(project).send(OxfmtConfigNotice.Imported(source.url, source.name))
    }
  }

  /** The folder whose config the project's code style follows: the project's own, or its first content root. */
  private fun projectFolder(): VirtualFile? =
    project.guessProjectDir() ?: ProjectRootManager.getInstance(project).contentRoots.firstOrNull()

  companion object {
    fun getInstance(project: Project): OxfmtCodeStyleAutoImporter = project.service()
  }
}
