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

  /**
   * Its `resolveConfig` only imports the config and returns the default export, which is all that reading
   * `fmt` needs, and it records each call in `calls.log` beside it.
   */
  fun vitePlus(fixture: CodeInsightTestFixture, folder: String = "node_modules/vite-plus") {
    fixture.addFileToProject("$folder/package.json", """
      { "name": "vite-plus", "version": "1.5.0", "type": "module", "bin": { "vp": "./bin/vp" },
        "exports": { ".": { "types": "./dist/index.d.ts", "import": "./dist/index.js", "require": "./dist/index.cjs" } } }
    """.trimIndent())
    fixture.addFileToProject("$folder/bin/vp", "#!/usr/bin/env node\n")
    fixture.addFileToProject("$folder/dist/index.js", """
      import { appendFileSync } from "node:fs";
      import { pathToFileURL } from "node:url";
      export async function resolveConfig({ configFile }, command) {
        appendFileSync(new URL("../calls.log", import.meta.url), `${'$'}{configFile} ${'$'}{command} ${'$'}{process.env.VP_VERSION}\n`);
        return (await import(pathToFileURL(configFile).href)).default;
      }
    """.trimIndent())
  }

  fun schema(title: String): String =
    """{ "${'$'}schema": "http://json-schema.org/draft-07/schema#", "title": "$title", "type": "object" }"""
}
