package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

/**
 * Runs a Fix All action on [file] the way the Project view's menu does, with its modal progress dialog,
 * and returns the notification it ends with.
 */
fun runFixAllAction(project: Project, disposable: Disposable, actionId: String, file: VirtualFile): Notification {
  val notifications = CopyOnWriteArrayList<Notification>()
  project.messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
    override fun notify(notification: Notification) {
      if (notification.groupId == "Oxc") notifications += notification
    }
  })
  val context = SimpleDataContext.builder()
    .add(CommonDataKeys.PROJECT, project)
    .add(CommonDataKeys.VIRTUAL_FILE, file)
    .build()
  val event = AnActionEvent.createEvent(context, Presentation(), ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP, null)

  ActionManager.getInstance().getAction(actionId).actionPerformed(event)

  waitUntil("the notification appears", 60.seconds) { notifications.isNotEmpty() }
  return notifications.single()
}
