package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.junit.Assert.assertFalse

private const val TRUST_CHECK_PROPERTY = "idea.trust.headless.disabled"

/**
 * Makes [project] count as untrusted until [disposable] is disposed. Unit-test mode trusts every project
 * unless this property says otherwise. A light test project is shared with later tests, so trust comes back.
 */
fun untrustProject(project: Project, disposable: Disposable) {
  val previous = System.getProperty(TRUST_CHECK_PROPERTY)
  System.setProperty(TRUST_CHECK_PROPERTY, "false")
  TrustedProjects.setProjectTrusted(project, false)
  Disposer.register(disposable) {
    if (!project.isDisposed) TrustedProjects.setProjectTrusted(project, true)
    if (previous == null) System.clearProperty(TRUST_CHECK_PROPERTY) else System.setProperty(TRUST_CHECK_PROPERTY, previous)
  }
  assertFalse("The project should count as untrusted", TrustedProjects.isProjectTrusted(project))
}
