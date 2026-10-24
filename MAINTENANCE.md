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
  With `OXC_TEST_WSL_DISTRO` set, the test JVM turns off the IDE's IJent agent for WSL (see `build.gradle.kts`):
  it needs a test-only class that the IDE does not ship. The WSL tests therefore use plain `\\wsl$` file access and
  `wsl.exe`, not IJent, which 2025.3+ IDEs use by default.
- CI runs the full suite on Linux (`Test`) and on Windows without WSL (`Test (Windows)`), and the WSL tests in a
  separate Windows job that installs Ubuntu 24.04 in WSL (`Test (WSL)`). Test data is checked out with LF line
  endings on every platform (see `.gitattributes`) because expected output is compared byte for byte.
- Integration tests (`src/integrationTest`) run a real WebStorm, at `platformVersion`, with the built plugin installed,
  using JetBrains' Starter and Driver frameworks (https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html).
  Run them with `./gradlew integrationTest`; they are not part of `check` (see the Kover setup in `build.gradle.kts`). Each test opens a project with Oxlint and Oxfmt
  installed by npm and checks diagnostics, Fix All and formatting. `LocalProjectIdeTest` runs anywhere with Node.js and npm
  on `PATH`. `WslProjectIdeTest` stores the project inside WSL and runs on Windows when `OXC_TEST_WSL_DISTRO` is set;
  unlike the unit tests, it keeps the IDE's defaults, IJent included. Starter downloads the IDE and writes logs and
  screenshots to `out/ide-tests`. CI runs them as a separate step after the unit tests in the `Test` (Linux) and
  `Test (WSL)` jobs; Gradle never runs them alongside the unit tests.
- `DisabledLspFeaturesTest` fails when the platform adds an LSP feature that the plugin does not switch off in
  `LspDisabledCustomization`.
- Tests whose KDoc starts with `QUESTIONABLE:` pin down current behavior that looks wrong. When fixing one of those
  behaviors, update the test to assert the correct behavior.
