# oxc-intellij-plugin

[![Build](https://github.com/oxc-project/oxc-intellij-plugin/workflows/Build/badge.svg)](https://github.com/oxc-project/oxc-intellij-plugin/actions/workflows/build.yml?query=branch%3Amain)
[![Version](https://img.shields.io/jetbrains/plugin/v/27061.svg)](https://plugins.jetbrains.com/plugin/27061-oxc)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/27061.svg)](https://plugins.jetbrains.com/plugin/27061-oxc)

<!-- Plugin description -->

# Oxc

The Oxidation Compiler is creating a suite of high-performance tools for JavaScript and TypeScript.

## Oxlint

A high-performance JavaScript/TypeScript linter.

## Oxfmt

A high-performance JavaScript/TypeScript formatter.

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Oxc"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/oxc-project/oxc-intellij-plugin/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

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
`com.intellij.platform.lsp:all` - Enable debug logging for LSP integrations.

---

# [Sponsored By](https://oxc.rs/sponsor)

<p align="center">
  <a href="https://oxc.rs/sponsor">
    <img src="https://raw.githubusercontent.com/oxc-project/sponsors/main/sponsors.svg" alt="Our sponsors" />
  </a>
</p>
