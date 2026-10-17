package com.github.oxc.project.oxcintellijplugin.testutil

import java.nio.file.Files
import java.nio.file.Path

/**
 * Package managers the plugin has to work with. Each one lays out `node_modules` differently,
 * which changes how IntelliJ resolves the `oxlint`/`oxfmt`/`vite-plus` packages and therefore
 * which binary the plugin launches and which directory becomes the language server root.
 *
 * - npm, Bun and Yarn (node-modules linker): hoisted, real directories.
 * - pnpm: `node_modules/<pkg>` is a symlink into `node_modules/.pnpm/...`.
 * - Yarn PnP: no `node_modules` at all; packages live in zip archives resolved by `.pnp.cjs`.
 */
enum class PackageManager(val displayName: String) {
    NPM("npm"),
    PNPM("pnpm"),
    YARN("Yarn"),
    YARN_PNP("Yarn PnP"),
    BUN("Bun");

    /** The command used to install a fixture's dependencies. */
    fun installCommand(tools: NodeTooling): List<String> = when (this) {
        NPM -> listOf(tools.node.toString(), tools.npmCli.toString(), "install", "--no-audit",
            "--no-fund", "--no-package-lock")
        PNPM -> listOf(tools.pnpm.toString(), "install", "--config.confirmModulesPurge=false")
        YARN, YARN_PNP -> listOf(tools.node.toString(), tools.yarnJs.toString(), "install")
        BUN -> listOf(tools.bun.toString(), "install", "--no-save")
    }

    /** Files the package manager needs next to `package.json` before installing. */
    fun prepareInstallDirectory(directory: Path) {
        when (this) {
            YARN -> {
                Files.writeString(directory.resolve(".yarnrc.yml"),
                    "nodeLinker: node-modules\nenableTelemetry: false\n")
                // An empty lockfile marks the directory as its own Yarn project, so Yarn does not
                // walk up and attach the fixture to an unrelated parent project.
                Files.writeString(directory.resolve("yarn.lock"), "")
            }

            YARN_PNP -> {
                // A project-local cache keeps the paths in `.pnp.cjs` relative to the project, so the
                // installed fixture still resolves after it is copied into the test project.
                Files.writeString(directory.resolve(".yarnrc.yml"),
                    "nodeLinker: pnp\nenableGlobalCache: false\nenableTelemetry: false\n")
                Files.writeString(directory.resolve("yarn.lock"), "")
            }

            NPM, PNPM, BUN -> Unit
        }
    }

    override fun toString(): String = displayName
}
