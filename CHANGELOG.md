<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# oxc-intellij-plugin Changelog

## [Unreleased]
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
