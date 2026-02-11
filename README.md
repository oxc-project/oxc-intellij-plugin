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

## Oxfmt

A high-performance JavaScript/TypeScript formatter.

- Format code via right-click context menu.
- Format code with the built-in actions and their shortcuts (<kbd>Code</kbd> > <kbd>Reformat Code</kbd>, <kbd>
  Code</kbd> > <kbd>Reformat File...</kbd>)
- Automatically format on save.
- JSON schema validation for `.oxfmtrc.json` configuration files.
- Configurable file extensions (.js, .jsx, .ts, .tsx, .cjs, .mjs, .cts, .mts).

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Oxc"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/oxc-project/oxc-intellij-plugin/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
