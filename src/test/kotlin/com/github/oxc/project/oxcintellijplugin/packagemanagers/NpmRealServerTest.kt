package com.github.oxc.project.oxcintellijplugin.packagemanagers

/** npm workspaces, which hoist every package they can to the root's `node_modules`. */
class NpmRealServerTest : PackageManagerRealServerTest() {
  override val manager = "npm"
  override val hoistsVitePlus = true
}
