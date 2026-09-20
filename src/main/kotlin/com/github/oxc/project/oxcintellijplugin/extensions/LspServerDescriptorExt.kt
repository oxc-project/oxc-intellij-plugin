package com.github.oxc.project.oxcintellijplugin.extensions

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor

/**
 * The URI of [root] as the platform announces it to the server.
 *
 * `LspServerDescriptor.createInitializeParams` builds `rootUri` and every `workspaceFolders`
 * entry with `getFileUri`, which resolves the path through `getFilePath` and therefore maps it
 * onto the target the server runs on. The server keys its per-workspace options by that URI and
 * echoes it back as the `scopeUri` of a `workspace/configuration` request, matching by string
 * equality. Any URI the plugin sends or compares for a root must come from this same call.
 */
fun LspServerDescriptor.workspaceUri(root: VirtualFile): String = getFileUri(root)
