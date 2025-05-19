<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# oxc-intellij-plugin Changelog

## [Unreleased]

### Added

- Support specifying a manual Oxc language server path to the binary instead of the Node.js wrapper.

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

[Unreleased]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.7...HEAD
[0.0.7]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/oxc-project/oxc-intellij-plugin/commits/v0.0.1
