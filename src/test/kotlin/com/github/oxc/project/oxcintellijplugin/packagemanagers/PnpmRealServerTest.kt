package com.github.oxc.project.oxcintellijplugin.packagemanagers

/** pnpm, which links each package from `node_modules/.pnpm`, a junction on Windows. */
class PnpmRealServerTest : PackageManagerRealServerTest() {
  override val manager = "pnpm"
  override val hoistsVitePlus = false
}
