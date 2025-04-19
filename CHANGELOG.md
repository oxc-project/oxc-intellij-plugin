<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# oxc-intellij-plugin Changelog

## [Unreleased]

### Fixed

- Fix Manual configuration to save the correct file path to the Oxlint config file.

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

[Unreleased]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.2...HEAD
[0.0.2]: https://github.com/oxc-project/oxc-intellij-plugin/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/oxc-project/oxc-intellij-plugin/commits/v0.0.1
