package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt

/** The platform's own Trust Project action, which asks with its trust dialog. */
private const val SHOW_TRUST_PROJECT_DIALOG = "ShowTrustProjectDialog"

private val LOG = Logger.getInstance("#com.github.oxc.project.oxcintellijplugin.backend.tool.ProjectTrust")

/**
 * Asks the user to trust [project] with the platform's dialog, and returns whether it's trusted afterwards.
 * The dialog is internal API, so this runs the platform's action, which shows it and stores the answer.
 */
@RequiresEdt
fun askToTrustProject(project: Project): Boolean {
  if (TrustedProjects.isProjectTrusted(project)) return true

  val action = ActionManager.getInstance().getAction(SHOW_TRUST_PROJECT_DIALOG)
  if (action == null) {
    LOG.warn("The platform has no $SHOW_TRUST_PROJECT_DIALOG action, so the project is trusted without its dialog")
    TrustedProjects.setProjectTrusted(project, true)
  }
  else {
    val event = AnActionEvent.createEvent(
      SimpleDataContext.getProjectContext(project),
      action.templatePresentation.clone(),
      ActionPlaces.NOTIFICATION,
      ActionUiKind.NONE,
      null,
    )
    ActionUtil.performAction(action, event)
  }
  return TrustedProjects.isProjectTrusted(project)
}
