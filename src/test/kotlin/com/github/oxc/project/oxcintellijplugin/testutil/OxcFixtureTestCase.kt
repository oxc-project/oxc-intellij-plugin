package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

/**
 * Base class for tests that use the project's Node interpreter. When [WslTesting] is enabled, the
 * interpreter is the WSL distribution's Node.js, so the same tests also cover a server running in WSL.
 */
abstract class OxcFixtureTestCase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  override fun setUp() {
    super.setUp()
    if (WslTesting.enabled) {
      NodeJsInterpreterManager.getInstance(project)
        .setInterpreterRef(WslTesting.nodeInterpreterRef(), testRootDisposable)
    }
  }
}
