## Release

1. Ensure that the UNRELEASED section of the [CHANGELOG.md](./CHANGELOG.md) is up to date with all relevant changes.
   See https://github.com/JetBrains/gradle-changelog-plugin for the changelog format.
2. Update the `pluginVersion` within [gradle.properties](./gradle.properties).
3. Every build creates a draft release that can be found
   here https://github.com/oxc-project/oxc-intellij-plugin/releases. These are all release candidates.
4. When ready to release, go to the current draft release and publish it. This will
   run [release.yml](./.github/workflows/release.yml) which will automatically create a PR to move the changes from the
   UNRELEASED section of the changelog to the appropriate version.

## Tests

Run all tests with `./gradlew test`. The only prerequisite is Node.js on `PATH` (or set `OXC_TEST_NODE` to the
node executable).

- Tests that run the real Oxlint/Oxfmt language servers copy a fixture from `src/test/testData` into the test project
  and install its `package.json` dependencies automatically. Nothing needs to be installed by hand.
- pnpm, Yarn and Bun are installed with npm into `build/oxc-test-tools` on first use, at the versions pinned in
  `NodeTooling`. Installed fixtures are cached in `build/oxc-test-fixtures`; delete it to force a reinstall.
- `packagemanagers/*` tests run the same scenarios with npm, pnpm, Yarn (node-modules linker) and Bun, plus Yarn
  Plug'n'Play.
- Tests for servers that crash, hang or return errors use `src/test/testData/fake-server/fake-lsp.js`, a scriptable
  language server (see `OxcLspTestCase.setUpFakeServer`).
- `MinimumVersionsTest` runs against the oldest supported Oxlint (1.29.0). Update its fixture when the minimum changes.
- Tests in the `windows` package only run on Windows, and the WSL ones only when `OXC_TEST_WSL_DISTRO` names an
  installed distribution with Node.js at `OXC_TEST_WSL_NODE` (default `/usr/bin/node`). They are skipped elsewhere.
  To run the WSL ones locally on Windows:
  `OXC_TEST_WSL_DISTRO=Ubuntu-24.04 ./gradlew test --tests 'com.github.oxc.project.oxcintellijplugin.windows.Wsl*'`.
- CI runs the full suite on Linux (`Test`) and on Windows without WSL (`Test (Windows)`), and the WSL tests in a
  separate Windows job that installs Ubuntu 24.04 in WSL (`Test (WSL)`). Test data is checked out with LF line
  endings on every platform (see `.gitattributes`) because expected output is compared byte for byte.
- `DisabledLspFeaturesTest` fails when the platform adds an LSP feature that the plugin does not switch off in
  `LspDisabledCustomization`.
- Tests whose KDoc starts with `QUESTIONABLE:` pin down current behavior that looks wrong. When fixing one of those
  behaviors, update the test to assert the correct behavior.
