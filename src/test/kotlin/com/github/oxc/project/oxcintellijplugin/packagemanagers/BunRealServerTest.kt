package com.github.oxc.project.oxcintellijplugin.packagemanagers

/** Bun's isolated installs, which link each package from `node_modules/.bun`, a junction on Windows. */
class BunRealServerTest : PackageManagerRealServerTest() {
  override val manager = "bun"
  override val hoistsVitePlus = false
}
