dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")
        bundledPlugins("JavaScript", "com.intellij.modules.json")
    }

    implementation(project(":shared"))
}
