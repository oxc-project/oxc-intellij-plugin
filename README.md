# oxc-intellij-plugin

[![Build](https://github.com/oxc-project/oxc-intellij-plugin/workflows/Build/badge.svg)](https://github.com/oxc-project/oxc-intellij-plugin/actions/workflows/build.yml?query=branch%3Amain)
[![Version](https://img.shields.io/jetbrains/plugin/v/27061.svg)](https://plugins.jetbrains.com/plugin/27061-oxc)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27061.svg)](https://plugins.jetbrains.com/plugin/27061-oxc)

<!-- Plugin description -->

# Oxc

The Oxidation Compiler is creating a suite of high-performance tools for JavaScript and TypeScript.

## Oxlint

A high-performance JavaScript/TypeScript linter.

- Inline diagnostics with highlighting for warnings and errors.
- Quick fixes to resolve issues when available.
- Command to apply all auto-fixable issues in the current editor.
- Automatically apply fixes on save.
  - Timeout for this is 30 seconds, but can be configured with an IntelliJ registry setting
    `oxc.lint.fix.all.timeout.ms`.
- Type-aware rules support for enhanced linting.
- Custom icons for Oxlint configuration files.
- JSON schema validation for `.oxlintrc.json` configuration files.
- Configurable file extensions (.js, .jsx, .ts, .tsx, .cjs, .mjs, .cts, .mts, .vue, .svelte, .astro).
- Vite+ Support

## Oxfmt

A high-performance JavaScript/TypeScript formatter.

- Format code via right-click context menu.
- Format code with the built-in actions and their shortcuts (<kbd>Code</kbd> > <kbd>Reformat Code</kbd>, <kbd>
  Code</kbd> > <kbd>Reformat File...</kbd>)
- Automatically format on save.
- JSON schema validation for `.oxfmtrc.json` configuration files.
- Configurable file formats JavaScript, TypeScript, JSON, HMYL, Markdown, MDX, CSS, SCSS, GrapghQL, TOML, YAML and
  more. [See the full list](https://github.com/oxc-project/oxc-intellij-plugin/blob/main/src/main/kotlin/com/github/oxc/project/oxcintellijplugin/oxfmt/settings/OxfmtSettingsState.kt).
- Vite+ Support

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Oxc"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/oxc-project/oxc-intellij-plugin/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Vite+

The plugin detects a direct `vite-plus` dependency in `dependencies` or `devDependencies`, starting
from each opened file. It searches parent directories up to the monorepo root, including
`pnpm-workspace.yaml`, `package.json` `workspaces`, and `lerna.json` boundaries. This also works when
only a subdirectory of a monorepo is open in the IDE.

Vite+ projects run `vp lint --lsp` and `vp fmt --lsp` from the package that declares the dependency.
The plugin prefers a local installation, then checks `PATH` and the configured Node interpreter's
default package locations. A transitive installation or global `vp` alone does not select Vite+.
If Vite+ is selected but unavailable, the plugin shows an install hint and leaves that tool stopped.
Install the dependencies, then use **Restart Oxlint Server** or **Restart Oxfmt Server** if the IDE
has not detected the installation yet.

Under **Settings/Preferences > Tools > Oxlint** and **Oxfmt**, select a **Binary source** independently:

- **Automatic** (default): use Vite+ when the project declares it, otherwise use the standalone tool.
- **Vite+**: use Vite+ even without a dependency declaration.
- **Standalone Oxc**: use the standalone tool and ignore the Vite+ executable setting.

Each tool also has an optional **Vite+ executable** path. An explicit path selects Vite+ in
Automatic mode. Relative paths start at the project content root. A language server path in
Manual configuration takes priority over both the source choice and the Vite+ path.
For example, select **Standalone Oxc** for Oxlint and **Vite+** for Oxfmt to combine their sources.

Vite+ servers always disable nested standalone configuration lookups so `vite.config.*` controls
their configuration. This does not change the saved nested-config setting for standalone tools.
Node entry points, including npm and pnpm shims, use the IDE's configured Node interpreter.
Use `vite-plus` `0.3.2` or later if that interpreter is the only available Node runtime and `node`
is absent from `PATH`.

## Troubleshooting

IntelliJ provides log files for standard logs as well as the LSP integration. The plugin uses the regular log
directories which can be found
here https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html#logs-directory.
All LSP logs are output to `language-services/Oxfmt*` or `language-services/Oxlint*`. Non-LSP logs are output to
`idea.log`.

The log level can be configured with the information available
here https://youtrack.jetbrains.com/articles/SUPPORT-A-43/How-to-enable-debug-logging-in-IntelliJ-IDEA.
`com.github.oxc.project.oxcintellijplugin:all` - Enable debug logging for the plugin.
`com.intellij.platform.lsp:all` - Enable debug logging for LSP integrations.

Enabling both debug logging for the plugin and debug logging for LSP integrations will typically provide useful
information for investigating problems.

---

# [Sponsored By](https://oxc.rs/sponsor)

<p align="center">
  <a href="https://oxc.rs/sponsor">
    <img src="https://raw.githubusercontent.com/oxc-project/sponsors/main/sponsors.svg" alt="Our sponsors" />
  </a>
</p>
