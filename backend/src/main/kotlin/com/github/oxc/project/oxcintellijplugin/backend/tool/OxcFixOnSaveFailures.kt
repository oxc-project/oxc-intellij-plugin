package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.github.oxc.project.oxcintellijplugin.shared.OxcFixOnSaveFailure
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Fix on save runs on the backend, and the frontend tells the user when it fails, because a notification
 * group is frontend API. This holds the failures until the frontend takes them.
 */
@Service(Service.Level.PROJECT)
class OxcFixOnSaveFailures {

  /** Holds a few until the frontend asks, which can be after the first save. */
  private val pending = Channel<OxcFixOnSaveFailure>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /** What the frontend should show. Each failure goes to one collector. */
  val failures: Flow<OxcFixOnSaveFailure>
    get() = pending.receiveAsFlow()

  fun report(failure: OxcFixOnSaveFailure) {
    pending.trySend(failure)
  }

  companion object {
    fun getInstance(project: Project): OxcFixOnSaveFailures = project.service()
  }
}
