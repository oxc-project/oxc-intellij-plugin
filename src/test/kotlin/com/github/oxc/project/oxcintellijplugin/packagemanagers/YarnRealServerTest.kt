package com.github.oxc.project.oxcintellijplugin.packagemanagers

/** Yarn 4 with the `node-modules` linker, which hoists like npm. */
class YarnRealServerTest : PackageManagerRealServerTest() {
  override val manager = "yarn"
  override val hoistsVitePlus = true
}
