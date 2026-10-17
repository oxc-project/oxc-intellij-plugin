package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.testutil.OxcLspTestCase
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.PlatformTestUtil
import java.net.URI

/**
 * The server is given file URIs in its own file system, and edits it returns for those URIs are
 * applied to the IDE's files. Locally both are the same path; for a server running inside WSL
 * (see `windows/WslServerPathsTest`) the plugin converts between Windows and WSL paths.
 */
abstract class ServerPathsTest : OxcLspTestCase() {

    /** The path the server sees for the local [path]. */
    protected abstract fun serverPath(path: String): String

    fun testServerIsGivenTheUrisOfTheRootAndOpenedFile() {
        setUpFakeServer(oxfmt = false)
        val file = openFile("index.js")
        waitForOxlintServer()

        val uris = waitForUris("textDocument/didOpen")

        val root = serverPath(refreshProjectDir().path)
        assertEquals(uris.toString(), root, uris.getValue("rootUri"))
        assertEquals(uris.toString(), root, uris.getValue("workspaceUri"))
        assertEquals(uris.toString(), serverPath(file.path), uris.getValue("textDocument/didOpen"))
    }

    fun testFixAllEditForTheServersUriIsAppliedToTheFile() {
        setUpFakeServer("""{ "fixAllText": "let a = { b: 1 };\n" }""", oxfmt = false)
        openFile("index.js")
        waitForOxlintServer()
        // The fake server builds the edit from the text it was sent when the file was opened.
        waitForUris("textDocument/didOpen")

        myFixture.testAction(ActionManager.getInstance().getAction("OxlintApplyFixAllAction"))

        PlatformTestUtil.waitWithEventsDispatching({ "Text: ${myFixture.editor.document.text}" },
            { myFixture.editor.document.text == "let a = { b: 1 };\n" }, 30)
    }

    /** Waits until the server logged a URI for [where] and returns the paths of all logged URIs by where they were given. */
    private fun waitForUris(where: String): Map<String, String> {
        PlatformTestUtil.waitWithEventsDispatching({ "URIs: ${fakeServerUris()}" },
            { fakeServerUris().any { it.startsWith("$where ") } }, 30)
        return fakeServerUris().associate { line ->
            val (key, uri) = line.split(" ", limit = 2)
            key to URI(uri).path.removeSuffix("/")
        }
    }
}

class LocalServerPathsTest : ServerPathsTest() {
    override fun serverPath(path: String) = path
}
