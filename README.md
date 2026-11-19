# oxc-intellij-plugin

[![Build](https://github.com/oxc-project/oxc-intellij-plugin/workflows/Build/badge.svg)](https://github.com/oxc-project/oxc-intellij-plugin/actions/workflows/build.yml?query=branch%3Amain)
[![Version](https://img.shields.io/jetbrains/plugin/v/27061.svg)](https://plugins.jetbrains.com/plugin/27061-oxc)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27061.svg)](https://plugins.jetbrains.com/plugin/27061-oxc)

<!-- Plugin description -->

# Oxc

The Oxidation Compiler is creating a suite of high-performance tools for JavaScript and TypeScript.

Oxlint and Oxfmt work in Remote Development (split mode), WSL and Dev Containers, and with Docker and SSH Node.js
interpreters.

## Oxlint

A high-performance JavaScript/TypeScript linter.

- Inline diagnostics with highlighting for warnings and errors.
- Quick fixes to resolve issues when available.
- Command to apply all auto-fixable issues to a file, open or closed.
- Automatically apply fixes on save, set on the Actions on Save page.
  - Timeout for this is 30 seconds per file, but can be configured with an IntelliJ registry setting
    `oxc.lint.fix.all.timeout.ms`.
- Type-aware rules support for enhanced linting.
- Custom icons for Oxlint configuration files.
- JSON schema validation for `.oxlintrc.json` configuration files, from the installed Oxlint.
- Configurable file extensions (.js, .jsx, .ts, .tsx, .cjs, .mjs, .cts, .mts, .vue, .svelte, .astro).
- Vite+ Support

## Oxfmt

A high-performance JavaScript/TypeScript formatter.

- Format code via right-click context menu.
- Format code with the built-in actions and their shortcuts (<kbd>Code</kbd> > <kbd>Reformat Code</kbd>, <kbd>
  Code</kbd> > <kbd>Reformat File...</kbd>)
- Automatically format on save, set on the Actions on Save page.
  - Timeout for this is 30 seconds per file, but can be configured with an IntelliJ registry setting
    `oxc.fmt.fix.all.timeout.ms`.
- Use the Oxfmt config as each file's code style, or import it into the project's code style.
- Custom icons for Oxfmt configuration files.
- JSON schema validation for `.oxfmtrc.json` configuration files, from the installed Oxfmt.
- Configurable file formats JavaScript, TypeScript, JSON, HTML, Markdown, MDX, CSS, SCSS, GraphQL, TOML, YAML and
  more. [See the full list](https://github.com/oxc-project/oxc-intellij-plugin/blob/main/shared/src/main/kotlin/com/github/oxc/project/oxcintellijplugin/shared/oxfmt/settings/OxfmtSettingsApi.kt).
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

## Remote Development

In Remote Development, the language servers run on the host, and the actions, settings pages and notifications run in
the client, so the client needs the plugin too. Without it, diagnostics and fix on save still work, but the actions
are missing.

## Remote Node.js interpreters

Oxlint and Oxfmt run with the project's Node.js interpreter, so they also run in WSL, Docker and over SSH.

With an SSH interpreter, the project has to be on the remote host, with its local folder mapped to the remote one, as
described in [Node.js via SSH](https://www.jetbrains.com/help/webstorm/node-via-ssh.html). Use a Deployment
configuration's mappings for that host, or the interpreter's path mappings. Without a mapping, the IDE uploads a copy
of the project when each server starts, and it doesn't update that copy. Config changes and fixes to files that aren't
open then use the old copy until the server is restarted.

## Troubleshooting

IntelliJ provides log files for standard logs as well as the LSP integration. The plugin uses the regular log
directories which can be found
here https://www.jetbrains.com/help/idea/directories-used-by-the-ide-to-store-settings-caches-plugins-and-logs.html#logs-directory.
All LSP logs are output to `language-services/Oxfmt*` or `language-services/Oxlint*`. Non-LSP logs are output to
`idea.log`.

The log level can be configured with the information available
here https://youtrack.jetbrains.com/articles/SUPPORT-A-43/How-to-enable-debug-logging-in-IntelliJ-IDEA.
`com.github.oxc.project.oxcintellijplugin:all` - Enable debug logging for the plugin. Language servers started
afterwards also log at that level, through `OXC_LOG` and `RUST_LOG`.
`com.intellij.platform.lsp:all` - Enable debug logging for LSP integrations.

Enabling both debug logging for the plugin and debug logging for LSP integrations will typically provide useful
information for investigating problems. In Remote Development, the language servers and their logs are on the host.

---

# [Sponsored By](https://oxc.rs/sponsor)

<p align="center">
  <a href="https://oxc.rs/sponsor">
    <img src="https://raw.githubusercontent.com/oxc-project/sponsors/main/sponsors.svg" alt="Our sponsors" />
  </a>
</p>
