package com.github.oxc.project.oxcintellijplugin.lsp

import com.github.oxc.project.oxcintellijplugin.backend.lsp.pathToFileUri
import junit.framework.TestCase

class ServerFileUriTest : TestCase() {

  fun testLinuxPath() {
    assertEquals("file:///mnt/c/Users/me/a.js", pathToFileUri("/mnt/c/Users/me/a.js"))
  }

  fun testSpecialCharactersAreEncoded() {
    assertEquals("file:///home/me/a%20b%23c.js", pathToFileUri("/home/me/a b#c.js"))
  }

  fun testWindowsDriveIsLowercaseWithAnEscapedColon() {
    assertEquals("file:///c%3A/Users/me/a.js", pathToFileUri("C:/Users/me/a.js"))
  }

  fun testNetworkPathKeepsItsHost() {
    assertEquals("file://wsl.localhost/Ubuntu/home/a.js", pathToFileUri("//wsl.localhost/Ubuntu/home/a.js"))
  }
}
