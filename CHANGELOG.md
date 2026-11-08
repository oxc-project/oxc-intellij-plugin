<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# oxc-intellij-plugin Changelog

## [Unreleased]

### Added

- Add support for IntelliJ split mode (Remote Development).
- Add support for Dev Containers opened in the IDE's own window, without a backend in the container. Oxlint and Oxfmt run in the container and get the container's paths for files.

### Changed

- **Breaking:** Rewrite the plugin to run in split mode.
- **Breaking:** Minimum IntelliJ version updated to 2026.3+.
- Remove the upper limit on supported IntelliJ versions.
- Oxlint now reloads changed config files itself, instead of the plugin restarting its language server.
- **Breaking:** The Oxlint fix kind and nested config settings move out of `flags` in `.idea/OxcSettings.xml`. Existing values are read, but older plugin versions can't read the new fields.
- Oxlint's manual config path is watched when it's outside the workspace, and changing it restarts the language server.
- Oxfmt also reloads changed config files itself. A manual config path outside the workspace is watched, and changing it restarts the language server.
- "Apply Oxfmt Quick Fixes" now says when a file is already formatted, shows cancellable progress without blocking the IDE, and can format a closed file. Its timeout is per file, 30 seconds by default, and set by the Registry key `oxc.fmt.fix.all.timeout.ms`.
- Oxfmt format on save no longer blocks the IDE. A failure is logged instead of shown as a notification.
- Oxfmt's fix on save is now only set on the Actions on Save page. The Oxfmt settings page links to it.
- "Prefer Oxfmt configuration to IDE code style" now also applies indent and margins for each file, including languages other than JavaScript and TypeScript.
- The Oxfmt code style and "Import Oxfmt Code Style" now use the same config as Oxfmt's language server, including a manual config path, "Disable nested config lookups", and an `oxfmt.config.ts` in a nearer folder. When that config is a TypeScript file, the code style is left alone, because the plugin can't read it.
- **Breaking:** Changing an Oxfmt config no longer rewrites the project's code style. Only "Import Oxfmt Code Style" does, and it now stores the result in the project rather than the IDE-wide scheme.
- "Import Oxfmt Code Style" now appears for every file that Oxfmt handles, and for folders, and says if there's no config.
- Oxfmt's default file extensions no longer list the five with more than one dot, such as `.json.example`. They never matched a file.
- File extensions in both tools' settings can now contain `-` and `_`.
- In a project in WSL or a Dev Container, a manual binary or config path can be written as it is there, such as `/workspaces/app/node_modules/.bin/oxlint`, as well as by the IDE's path to it.
- Only `fmt.configPath` and `fmt.disableNestedConfig` are sent to Oxfmt's language server, which are the settings it reads.
- In a Vite+ project, `vp` reloads a changed `vite.config.*` itself, instead of the plugin restarting the language servers.
- In a Vite+ project, the Oxfmt code style and Import use the same config as `vp fmt`: the first `vite.config.*` with a `fmt` field, from the folder `vp` runs in upward. `.oxfmtrc.json` and `oxfmt.config.ts` aren't used there, because `vp fmt` ignores them.
- The JSON schemas for `.oxlintrc.json(c)` and `.oxfmtrc.json(c)`, and for a manual config path, now come from the Oxlint or Oxfmt package that runs for the file, so they match its version and need no network. In a Vite+ project, that's the Oxlint or Oxfmt that `vp` runs. Without an installed package there's no schema, and the schema catalog's copy from Oxc's `main` branch isn't used for these files.
- A manual config path only gets the Oxc icon in Manual mode, which is the only mode that uses it.

### Fixed

- With more than one client connected, Oxlint's language server now keeps running while any client has a supported file open.
- A manual Oxlint or Oxfmt binary at a path into WSL now runs inside WSL, instead of through Node.
- Oxfmt no longer corrupts a file when the language server returns more than one edit.
- An unknown `trailingComma` value in an Oxfmt config no longer causes an error.
- A config change only affects the project that it's in.
- Oxfmt's manual config path now gets the Oxc icon, as Oxlint's does.
- `oxlint.config.mts` and `oxfmt.config.mts` now get the Oxc icon.

## [0.0.45] - 2026-10-15

### Added

- Add Astro to the Oxfmt default file extensions.

## [0.0.44] - 2026-09-27

### Added

- Add a setting to disable nested config lookups for Oxfmt.

### Removed

- **Breaking:** Remove the "Add --lsp CLI argument" setting for manual Oxlint configurations. The `--lsp` flag is now always
  supplied.
- **Breaking:** Oxlint versions older than 1.29.0 (`oxc_language_server`) are no longer supported.

### Fixed

- When using a manual configuration pointing to a non-Node.js file on Windows, execute with cmd.exe instead of directly.
- Add back code actions support for Oxlint. Broken as part of plugin version 0.0.41.

## [0.0.43] - 2026-09-25

### Changed

- Launch `vp lint --lsp` and `vp fmt --lsp` for Vite+ projects, since Vite+ 1.0 (RC) no longer ships the `oxlint` and
  `oxfmt` wrapper bins. (Do not fall back to a globally installed `vite-plus`.)

## [0.0.42] - 2026-09-25

### Fixed

- Fix pluginUntilBuild to be correctly set on publish, so that it properly reflects in the Marketplace and for installations.

## [0.0.41] - 2026-09-25

### Changed

- **Breaking:** Minimum IntelliJ version updated to 2025.3+.
- Configure 2026.2 as the highest supported IntelliJ version. There are known compatibility issues with 2026.3 that need
  to be resolved.
- Migrate from deprecated LSP support to current alternative.

## [0.0.40] - 2026-09-21

### Fixed

- Update diagnostics for Oxlint to be agnostic of the underlying Diagnostic implementation. This
  allows the same code to work for existing IntelliJ versions and post 2026.3 versions which use
  lsp4j 1.0.0 and newer.
- Ensure that URIs are created consistently when they are used for comparison, so that workspace
  configurations are correctly looked up instead of falling back to defaults.

## [0.0.39] - 2026-07-31

### Changed

- Oxlint uses pull diagnostics instead of push diagnostics. The lint on type/save option has been removed from the
  settings to simplify things, but if there's a need to add either option back open an issue and it can be discussed.

## [0.0.38] - 2026-07-19

### Added

- Add support for jsonc files with the Oxfmt code style importer.

### Fixed

- Oxfmt save action runs after Oxlint and other save actions (such as optimize imports).

## [0.0.37] - 2026-07-12

### Added

- Add support for applying Oxfmt code style which will update the IntelliJ code style settings to match the Oxfmt
  settings.
  - This only works for JSON config files. TypeScript/JavaScript files could probably be added with some effort.
  - This can be enabled within the Oxfmt settings.
  - Code style should be automatically applied when modifying a config file. If necessary, there is an action for
    "Import Oxfmt Code Style".

### Fixed

- Warm the WSL mntRoot cache, to avoid synchronous execution on the EDT errors.

## [0.0.36] - 2026-07-01

### Changed

- Oxlint fix all on save now runs in the background instead of a blocking modal.
- Oxlint fix all on save timeout has been changed to 30 seconds and is configurable with a registry setting
  `oxc.lint.fix.all.timeout.ms`.

## [0.0.35] - 2026-06-17

### Added

- Oxfmt and Oxlint can be restarted using an action now (typically from the shift + shift shortcut) instead of only from
  the toolbar.
- Add Svelte to the Oxfmt default file extensions.

### Changed

- When triggering Oxlint fix all action, only show notification if changes were actually applied.
- When Oxlint `fixKind` is set to none and fixes are triggered through the Oxlint fix all action or fix all on save
  action, don't send a message to the language server.

### Fixed

- When Oxlint `fixKind` is configured for dangerous fixes, the Oxlint fix all action and fix all on save action will now
  correctly apply the dangerous fixes.

## [0.0.34] - 2026-06-10

### Changed

- Workflow changes to support GitHub immutable releases. No real plugin changes.
  This may fail to release correctly. (Hopefully the last one, really this time.)

## [0.0.33] - 2026-05-31

### Changed

- Workflow changes to support GitHub immutable releases. No real plugin changes.
  This may fail to release correctly. (Hopefully the last one.)

## [0.0.32] - 2026-05-31

### Changed

- Workflow changes to support GitHub immutable releases. No real plugin changes.
  This may fail to release correctly. (Hopefully the last one.)

## [0.0.31] - 2026-05-20

### Changed

- Workflow changes to support GitHub immutable releases. No real plugin changes.
  This may fail to release correctly.

## [0.0.30] - 2026-05-20

### Changed

- Workflow changes to support GitHub immutable releases. No real plugin changes.
  This failed to release correctly and was never published to JetBrains.

## [0.0.29] - 2026-05-09

### Added

- Add support for detecting Vite+ installations and using the correct tools from that installation.

## [0.0.28] - 2026-04-11

### Added

- Add support to restart the language server automatically for oxfmt.config.ts, oxlint.config.ts, and vite.config.{js,ts}.
- Add icon for oxfmt.config.ts and oxlint.config.ts.

### Fixed

- Properly resolve nested configs for Oxlint.

## [0.0.27] - 2026-04-10

### Added

- Support additional file extensions by default with Oxfmt.

### Fixed

- Fix error that read access is only allowed within a read action.

## [0.0.26] - 2026-04-01

### Added

- Remote interpreters (such as SSH, Docker, etc.) should now be supported.

### Fixed

- Yarn PnP should now properly detect content root and enable the Oxfmt and Oxlint language servers.

## [0.0.25] - 2026-03-08

### Fixed

- Remove trailing slash from workspaceUri which causes the language server to behave differently.

## [0.0.24] - 2026-01-26

### Fixed

- Fix icon size.

## [0.0.23] - 2026-01-25

### Added

- Add support for using the plugin within free IDEs.

### Changed

- Minimum IDE version has been updated to 2025.2.1.
- Update to new Oxc icon.

## [0.0.22] - 2025-12-24

### Fixed

- Normalize text newlines when using Oxfmt fixes or format on save.

## [0.0.21] - 2025-12-09

### Added

- Add support for Oxfmt.
  - Format on save
  - Reformat code action
  - Right click and apply quick fixes
  - New settings for Oxfmt separate from the Oxlint settings

### Fixed

- Fixed WSL with PNPM usage.

## [0.0.20] - 2025-11-19

### Added

- Added support for the upcoming 1.29.0 version of Oxlint which allows executing the language server through
  `oxlint --lsp`. When using automatic configuration, this will be accommodated without change when updating to
  a newer version of Oxlint. When using a manual configuration with a custom path to the language server, there is
  a new checkbox that will need to be checked after updating.

## [0.0.19] - 2025-11-02

### Changed

- Replace flags configuration with dedicated UI components. The Oxc language server is shifting away from a generic
  flags approach and this matches it. This also provides a better UI to users by exposing these options directly.

## [0.0.18] - 2025-10-19

### Changed

- `pluginUntilBuild` version is not specified anymore. Plugin will be marked as compatible with all future versions of
  an IntelliJ IDE. Individual versions will be marked as not compatible within the JetBrains Marketplace as needed.

## [0.0.17] - 2025-09-18

### Added

- Update pluginUntilBuild version for compatibility with 2025.3 versions of IntelliJ.

## [0.0.16] - 2025-08-30

### Fixed

- Fix working directory lookup when specifying a language server path that is not part of a node_modules installation.

## [0.0.15] - 2025-08-28

### Added

- Add support for type aware rules.
- Cleanup the configuration UI.

## [0.0.14] - 2025-08-28

### Added

- Add support for configuring the severity of unused disable directives.
- Add support for passing flags to the language server.

### Fixed

- Fix `fixAllOnSave` property to save to the correct element instead of conflicting with `configPath`.

## [0.0.13] - 2025-07-04

### Fixed

- Resolve multiple language servers being started when only one should be running.
  This resulted in duplicate diagnostics in the IDE.
- Resolve language server not stopping when closing all relevant files.
- Resolve language server starting when opening irrelevant files.

### Removed

- Removed support for IDE versions 2024.3.

## [0.0.12] - 2025-06-24

### Added

- Add support for 2025.2.* versions of IntelliJ.

## [0.0.11] - 2025-06-11

### Fixed

- Use project root directory as the root when an Oxlint config file is not available.

## [0.0.10] - 2025-06-09

### Added

- Add run trigger to config settings. Allow triggering Oxlint either on save or on type.

### Changed

- Use the new Oxc Language Server config format. This requires Oxlint 0.16.11 or newer.

## [0.0.8] - 2025-05-19

### Added

- Support specifying a manual Oxc language server path to the binary instead of the Node.js wrapper.

### Fixed

- Only show "Apply Oxc Quick Fixes" when the plugin is enabled and the selected file is a supported file extension.

## [0.0.7] - 2025-05-12

### Added

- Add quick fixes back to the file explorer.

## [0.0.6] - 2025-05-05

### Added

- If the RUST_LOG env variable is undefined, pass either DEBUG or TRACE to the process when starting the language server
  for additional logging when the plugin is configured to output debug or trace logs.

### Fixed

- Additional fixes to support custom Oxlint config files.
- Make the language server config path optional when using the manual config.

## [0.0.5] - 2025-05-01

### Fixed

- Fix support for custom Oxlint config files.
- Remove quick fix from file explorer context menu until the action supports it.

## [0.0.4] - 2025-04-25

### Fixed

- Fix diagnostic may not have a code causing a NullPointerException to be thrown. 
  ...Same as before, missed one spot for this.

## [0.0.3] - 2025-04-22

### Changed

- Improve the settings UI.

### Fixed

- Fix diagnostic may not have a code causing a NullPointerException to be thrown.
- Stop showing the server restart message when enabling/disabling the plugin setting.

## [0.0.2] - 2025-04-15

### Fixed

- Fix Oxc icon size.

## [0.0.1] - 2025-04-12

### Added

- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Integration with Oxc language server for lint abilities (oxlint)
  - Highlighting for warnings or errors identified by Oxlint.
  - Quick fixes to fix a warning or error when possible.
  - Command to fix all auto-fixable content within the current text editor.
  - Automatically apply fixes on save.
- Custom icons for Oxlint config files
- Schema validation for `.oxlintrc.json` configuration files. (Note: Comments within the .oxlintrc.json
  file are supported, however they show as an error within the IDE due to jsonc not being supported by the IDE.)

[Unreleased]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.45...HEAD
[0.0.45]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.44...v0.0.45
[0.0.44]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.43...v0.0.44
[0.0.43]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.42...v0.0.43
[0.0.42]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.41...v0.0.42
[0.0.41]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.40...v0.0.41
[0.0.40]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.39...v0.0.40
[0.0.39]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.38...v0.0.39
[0.0.38]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.37...v0.0.38
[0.0.37]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.36...v0.0.37
[0.0.36]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.35...v0.0.36
[0.0.35]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.34...v0.0.35
[0.0.34]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.33...v0.0.34
[0.0.33]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.32...v0.0.33
[0.0.32]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.31...v0.0.32
[0.0.31]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.30...v0.0.31
[0.0.30]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.29...v0.0.30
[0.0.29]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.28...v0.0.29
[0.0.28]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.27...v0.0.28
[0.0.27]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.26...v0.0.27
[0.0.26]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.25...v0.0.26
[0.0.25]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.24...v0.0.25
[0.0.24]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.23...v0.0.24
[0.0.23]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.22...v0.0.23
[0.0.22]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.21...v0.0.22
[0.0.21]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.20...v0.0.21
[0.0.20]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.19...v0.0.20
[0.0.19]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.18...v0.0.19
[0.0.18]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.17...v0.0.18
[0.0.17]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.16...v0.0.17
[0.0.16]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.15...v0.0.16
[0.0.15]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.14...v0.0.15
[0.0.14]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.13...v0.0.14
[0.0.13]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.12...v0.0.13
[0.0.12]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.11...v0.0.12
[0.0.11]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.10...v0.0.11
[0.0.10]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.8...v0.0.10
[0.0.8]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/oxc-project/oxc-intellij-plugin/commits/v0.0.1
