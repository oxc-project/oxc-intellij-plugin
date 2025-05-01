<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# oxc-intellij-plugin Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.4...HEAD
[0.0.4]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/oxc-project/oxc-intellij-plugin/commits/v0.0.1
