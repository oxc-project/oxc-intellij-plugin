<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# oxc-intellij-plugin Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.20...HEAD
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
