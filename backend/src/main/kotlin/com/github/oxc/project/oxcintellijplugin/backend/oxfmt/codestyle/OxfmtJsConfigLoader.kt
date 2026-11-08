package com.github.oxc.project.oxcintellijplugin.backend.oxfmt.codestyle

import com.github.oxc.project.oxcintellijplugin.backend.OxcBackendBundle
import com.github.oxc.project.oxcintellijplugin.backend.process.startWithoutProgress
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtConfigNotice
import com.github.oxc.project.oxcintellijplugin.shared.oxfmt.OxfmtJsConfigFailure
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.ProcessOutput
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.javascript.nodejs.execution.NodeTargetRun
import com.intellij.javascript.nodejs.execution.NodeTargetRunOptions
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

private const val SCRIPT_RESOURCE = "/oxc/load-oxfmt-config.mjs"
private const val TIMEOUT_REGISTRY_KEY = "oxc.fmt.config.load.timeout.ms"
private const val DEFAULT_TIMEOUT_MS = 30_000

/** What reading a JavaScript or TypeScript config through Node gave. */
sealed interface OxfmtJsConfigResult {

  /** [file] had the options, which under `vp` can be a config above the first one. */
  data class Loaded(val file: VirtualFile, val config: OxfmtConfig) : OxfmtJsConfigResult

  /** No `vite.config.*` had a `fmt` field, so the server uses its defaults. */
  data object Missing : OxfmtJsConfigResult

  /** Reading it would run the project's code, which only happens in a trusted project. */
  data class Untrusted(val file: VirtualFile) : OxfmtJsConfigResult

  /** [details] is everything known about it, such as the error's stack and what Node printed. */
  data class Failed(
    val file: VirtualFile,
    val reason: OxfmtJsConfigFailure,
    val message: String,
    val details: String = message,
  ) : OxfmtJsConfigResult
}

/**
 * Reads JavaScript and TypeScript configs by running them with the project's Node, which is what the server
 * does, and only in a trusted project. Each config is read once until it, the interpreter or the project's
 * trust changes. A file that the config imports isn't watched, so changing one doesn't read it again.
 *
 * Nothing hears about an interpreter change, because the JavaScript plugin's listener is internal. The
 * interpreter is part of each result's key, so the next file that asks reads the config again. A file whose
 * overlay is already cached doesn't ask until it's reopened, the config changes, or Import runs, except
 * after Configure Node.js, which calls [retryNodeFailures]. Import reads a failed config again.
 *
 * A config that can't be read gets one notification until something changes. An untrusted project gets one
 * for the whole session, unless Import asks. The frontend shows them, as [notices] sends them.
 */
@Service(Service.Level.PROJECT)
class OxfmtJsConfigLoader(private val project: Project, private val scope: CoroutineScope) : Disposable {

  /**
   * [generation] is [nodeRetries] when it was made. [previous] is the failure that it reads again, so the
   * same failure isn't shown twice.
   */
  private class Entry(
    val key: Key,
    val result: Deferred<OxfmtJsConfigResult>,
    val generation: Int,
    val previous: OxfmtJsConfigResult.Failed?,
  ) {
    val notified = AtomicBoolean()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun failure(): OxfmtJsConfigResult.Failed? =
      result.takeIf { it.isCompleted && !it.isCancelled }?.getCompleted() as? OxfmtJsConfigResult.Failed
  }

  /** Everything that a result depends on. */
  private data class Key(
    val files: List<FileStamp>,
    val format: OxfmtConfigFormat,
    val explicit: Boolean,
    val root: String?,
    val vitePlusFolder: String?,
    val interpreter: String?,
    val trusted: Boolean,
  )

  private data class FileStamp(val path: String, val modificationStamp: Long, val timeStamp: Long)

  private val entries = ConcurrentHashMap<String, Entry>()
  private val untrustedNotified = AtomicBoolean()

  /** Counts [retryNodeFailures] calls. An entry older than this that failed for want of Node is read again. */
  private val nodeRetries = AtomicInteger()

  /** Holds a few notices until the frontend asks, which can be after the first files open. */
  private val pending = Channel<OxfmtConfigNotice>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /** What the frontend should show. Each notice goes to one collector. */
  val notices: Flow<OxfmtConfigNotice>
    get() = pending.receiveAsFlow()

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
      override fun onProjectTrusted(project: Project) = trustChanged(project)
      override fun onProjectUntrusted(project: Project) = trustChanged(project)
    })
  }

  /**
   * Doesn't block. Returns null until the config has been read, and then refreshes the overlay so that it
   * asks again.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun getNow(location: OxfmtConfigLocation): OxfmtJsConfigResult? {
    val result = entry(location).result
    return if (result.isCompleted && !result.isCancelled) result.getCompleted() else null
  }

  /**
   * For Import, which reads a failed config again and shows its problem even if it was shown already,
   * because someone asked. Import gets no second notification for a config that's still being read, because
   * reading it shows one.
   */
  suspend fun load(location: OxfmtConfigLocation): OxfmtJsConfigResult {
    while (true) {
      // The key is made again each time, from the files' current stamps.
      val (entry, wasRead) = readAction { entry(location, retryFailure = true).let { it to it.result.isCompleted } }
      val result = try {
        entry.result.await()
      }
      catch (e: CancellationException) {
        // The config changed while it was being read, so the newer read replaced this one.
        currentCoroutineContext().ensureActive()
        continue
      }
      if (wasRead) display(result) else show(entry, result, always = true)
      return result
    }
  }

  private fun entry(location: OxfmtConfigLocation, retryFailure: Boolean = false): Entry {
    val key = keyOf(location)
    val generation = nodeRetries.get()
    var created: Entry? = null
    val entry = entries.compute(location.file.path) { _, current ->
      val failure = current?.takeIf { it.key == key }?.failure()
      val retry = failure != null &&
                  (retryFailure || failure.reason != OxfmtJsConfigFailure.ERROR && current.generation < generation)
      if (current != null && current.key == key && !retry) return@compute current
      current?.result?.cancel()
      Entry(key, scope.async(start = CoroutineStart.LAZY) { read(location, key) }, generation, failure).also { created = it }
    }!!
    created?.let { started ->
      started.result.start()
      scope.launch {
        val result = started.result.await()
        // Unclaimed, so that Import can still show it.
        if (!sameFailure(started.previous, result)) show(started, result, always = false)
        refreshOverlay()
      }
    }
    return entry
  }

  private fun sameFailure(previous: OxfmtJsConfigResult.Failed?, result: OxfmtJsConfigResult): Boolean =
    previous != null && result is OxfmtJsConfigResult.Failed &&
    previous.reason == result.reason && previous.message == result.message

  private fun keyOf(location: OxfmtConfigLocation): Key = Key(
    files = (listOf(location.file) + location.fallbacks).map { FileStamp(it.path, it.modificationStamp, it.timeStamp) },
    format = location.format,
    explicit = location.explicit,
    root = location.launch?.root?.path,
    vitePlusFolder = location.launch?.vitePlusFolder,
    interpreter = NodeJsInterpreterManager.getInstance(project).interpreterRef.referenceName,
    trusted = TrustedProjects.isProjectTrusted(project),
  )

  private suspend fun read(location: OxfmtConfigLocation, key: Key): OxfmtJsConfigResult {
    if (!key.trusted) return OxfmtJsConfigResult.Untrusted(location.file)

    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
                      ?: return failed(location.file, OxfmtJsConfigFailure.NO_NODE, OxcBackendBundle.message("node.interpreter.not.configured"))
    interpreter.validate(project)?.let { return failed(location.file, OxfmtJsConfigFailure.NO_NODE, it) }

    val files = listOf(location.file) + location.fallbacks
    val output = try {
      runInterruptible(Dispatchers.IO) { runScript(location, files) }
    }
    catch (e: Exception) {
      if (e is CancellationException) throw e
      thisLogger().info("Couldn't run Node for ${location.file.path}", e)
      return failed(location.file, OxfmtJsConfigFailure.ERROR, e.message ?: e.toString(), detail = e.stackTraceToString())
    }
    return parseOutput(location, files, output)
  }

  private fun runScript(location: OxfmtConfigLocation, files: List<VirtualFile>): ProcessOutput {
    val interpreter = NodeJsInterpreterManager.getInstance(project).interpreterOrThrow
    val run = NodeTargetRun(interpreter, project, null, NodeTargetRunOptions.of(false))
    run.commandLineBuilder.apply {
      setWorkingDirectory(run.path((location.launch?.root ?: location.file.parent).path))
      addParameter(run.path(scriptFile()))
      when (location.format) {
        OxfmtConfigFormat.VITE_PLUS -> {
          addParameter("vite-plus")
          addParameter(run.path(checkNotNull(location.launch?.vitePlusFolder) { "No vite-plus package for ${location.file.path}" }))
        }
        else -> addParameter("javascript")
      }
      files.forEach { addParameter(run.path(it.path)) }
      charset = Charsets.UTF_8
    }
    val handler = startWithoutProgress { run.startProcessEx().processHandler }
    val timeout = Registry.intValue(TIMEOUT_REGISTRY_KEY, DEFAULT_TIMEOUT_MS).takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
    return CapturingProcessRunner(handler).runProcess(timeout, true)
  }

  private suspend fun parseOutput(location: OxfmtConfigLocation, files: List<VirtualFile>, output: ProcessOutput): OxfmtJsConfigResult {
    if (output.isTimeout) {
      return failed(location.file, OxfmtJsConfigFailure.ERROR, OxcBackendBundle.message("oxfmt.js.config.timed.out", location.file.name), output)
    }
    val result = output.stdoutLines.lastOrNull { it.isNotBlank() }
      ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
                 ?: return failed(location.file, OxfmtJsConfigFailure.ERROR,
                                  output.stderr.trim().ifEmpty { OxcBackendBundle.message("oxfmt.js.config.no.answer", output.exitCode) }, output)

    fun file(): VirtualFile = result["index"]?.jsonPrimitive?.int?.let(files::getOrNull) ?: location.file
    fun message(): String = result["message"]?.jsonPrimitive?.content.orEmpty()

    return when (result["kind"]?.jsonPrimitive?.content) {
      "loaded" -> {
        val config = result["config"] as? JsonObject
                     ?: return failed(file(), OxfmtJsConfigFailure.ERROR, OxcBackendBundle.message("oxfmt.js.config.no.answer", output.exitCode), output)
        val file = file()
        OxfmtJsConfigResult.Loaded(file, readAction { OxfmtConfig.parse(project, "${file.name}.json", config.toString()) })
      }
      // The server reads a manual config's `fmt` field and calls it an error when there isn't one.
      "missing" -> if (location.explicit) {
        failed(location.file, OxfmtJsConfigFailure.ERROR, OxcBackendBundle.message("oxfmt.js.config.no.fmt", location.file.name))
      }
      else OxfmtJsConfigResult.Missing
      "node-version" -> failed(file(), OxfmtJsConfigFailure.OLD_NODE, OxcBackendBundle.message(
        "oxfmt.js.config.old.node", result["version"]?.jsonPrimitive?.content.orEmpty()), output)
      else -> failed(file(), OxfmtJsConfigFailure.ERROR, message(), output, result["detail"]?.jsonPrimitive?.content)
    }
  }

  /** [detail] is the error's stack. It goes to the log and the details, with what Node printed to stderr. */
  private fun failed(
    file: VirtualFile,
    reason: OxfmtJsConfigFailure,
    message: String,
    output: ProcessOutput? = null,
    detail: String? = null,
  ): OxfmtJsConfigResult.Failed {
    val stderr = output?.stderr?.trim().orEmpty()
    val details = buildString {
      append(detail?.takeIf { message in it } ?: listOfNotNull(message, detail).joinToString("\n\n"))
      if (stderr.isNotEmpty() && stderr != message) append("\n\n").append(OxcBackendBundle.message("oxfmt.js.config.details.stderr")).append('\n').append(stderr)
    }
    if (output != null || detail != null) {
      thisLogger().info("Node couldn't read ${file.path}${output?.let { " (exit ${it.exitCode})" }.orEmpty()}: $details")
    }
    return OxfmtJsConfigResult.Failed(file, reason, message, details)
  }

  /**
   * Shows a result's problem once. An untrusted project is shown once a session, unless [always]. Each
   * check claims its flag in the same step, because two reads can finish at the same time.
   */
  private fun show(entry: Entry, result: OxfmtJsConfigResult, always: Boolean) {
    if (result !is OxfmtJsConfigResult.Untrusted && result !is OxfmtJsConfigResult.Failed) return
    if (!entry.notified.compareAndSet(false, true)) return
    if (result is OxfmtJsConfigResult.Untrusted && !always && !untrustedNotified.compareAndSet(false, true)) {
      // Not shown, so Import can still show it for this config.
      entry.notified.set(false)
      return
    }
    display(result)
  }

  private fun display(result: OxfmtJsConfigResult) {
    val notice = when (result) {
      is OxfmtJsConfigResult.Untrusted -> {
        untrustedNotified.set(true)
        OxfmtConfigNotice.Untrusted(result.file.url, result.file.name)
      }
      is OxfmtJsConfigResult.Failed -> OxfmtConfigNotice.Failed(result.file.url, result.file.name, result.reason, result.message, result.details)
      else -> return
    }
    pending.trySend(notice)
  }

  private fun trustChanged(changed: Project) {
    if (changed == project) refreshOverlay()
  }

  /**
   * Reads configs that failed for want of Node again, as files ask. A fix can keep the interpreter's name,
   * such as Node installed again at the same path. A config that fails the same way isn't shown again.
   */
  fun retryNodeFailures() {
    nodeRetries.incrementAndGet()
    refreshOverlay()
  }

  /** Makes files ask the overlay again, which reads anything that changed. */
  private fun refreshOverlay() {
    ApplicationManager.getApplication().invokeLater({ refreshOxfmtOverlay(project) }, project.disposed)
  }

  override fun dispose() {
    pending.close()
  }

  companion object {
    fun getInstance(project: Project): OxfmtJsConfigLoader = project.service()

    /** The script, written where Node can read it, once for each version of it. */
    private val script: Path by lazy {
      val bytes = checkNotNull(OxfmtJsConfigLoader::class.java.getResourceAsStream(SCRIPT_RESOURCE)) { SCRIPT_RESOURCE }.use { it.readBytes() }
      val hash = MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(it) }
      val path = PathManager.getSystemDir().resolve("oxc").resolve("load-oxfmt-config-$hash.mjs")
      if (!Files.exists(path)) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, "load-oxfmt-config", ".tmp")
        Files.write(temporary, bytes)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      }
      path
    }

    private fun scriptFile(): String = script.toString()
  }
}
