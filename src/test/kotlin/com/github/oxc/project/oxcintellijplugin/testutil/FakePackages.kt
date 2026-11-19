package com.github.oxc.project.oxcintellijplugin.testutil

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
 * Packages with nothing in them but what the plugin looks at, for tests that don't start a server. A
 * schema's `title` says which copy it is.
 */
object FakePackages {

  fun tool(
    fixture: CodeInsightTestFixture,
    folder: String,
    name: String,
    version: String = "1.0.0",
    schema: String = schema("$folder $version"),
  ): VirtualFile {
    fixture.addFileToProject("$folder/package.json", """{ "name": "$name", "version": "$version", "bin": "bin/$name" }""")
    fixture.addFileToProject("$folder/bin/$name", "#!/usr/bin/env node\n")
    return fixture.addFileToProject("$folder/configuration_schema.json", schema).virtualFile
  }

  fun vitePlus(fixture: CodeInsightTestFixture, folder: String = "node_modules/vite-plus") {
    fixture.addFileToProject("$folder/package.json", """{ "name": "vite-plus", "version": "1.5.0", "bin": { "vp": "./bin/vp" } }""")
    fixture.addFileToProject("$folder/bin/vp", "#!/usr/bin/env node\n")
  }

  fun schema(title: String): String =
    """{ "${'$'}schema": "http://json-schema.org/draft-07/schema#", "title": "$title", "type": "object" }"""
}
