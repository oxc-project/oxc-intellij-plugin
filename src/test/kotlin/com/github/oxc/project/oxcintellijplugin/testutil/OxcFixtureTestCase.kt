package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

/** Base class for tests that need a project of their own, rather than the light project that tests share. */
abstract class OxcFixtureTestCase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>()
