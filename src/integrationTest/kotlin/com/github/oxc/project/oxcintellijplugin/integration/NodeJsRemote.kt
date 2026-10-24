package com.github.oxc.project.oxcintellijplugin.integration

import com.intellij.driver.client.Remote

/** The project's Node.js interpreter setting (Settings | Languages & Frameworks | Node.js). */
@Remote("com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager", plugin = "JavaScript")
interface NodeJsInterpreterManager {
    fun setInterpreterRef(ref: NodeJsInterpreterRef)
    fun getInterpreterRef(): NodeJsInterpreterRef
}

@Remote("com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef", plugin = "JavaScript")
interface NodeJsInterpreterRef {
    /** Static `NodeJsInterpreterRef.create(String)`: a path, or `wsl://<distro>@<path>` for Node.js inside WSL. */
    fun create(reference: String): NodeJsInterpreterRef
    fun getReferenceName(): String
}
