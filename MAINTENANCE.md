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
- Tests whose KDoc starts with `QUESTIONABLE:` pin down current behavior that looks wrong. When fixing one of those
  behaviors, update the test to assert the correct behavior.
