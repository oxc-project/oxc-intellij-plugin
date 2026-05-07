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
- Configurable run trigger: lint on type or on save.
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
