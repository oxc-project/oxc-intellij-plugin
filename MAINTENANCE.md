## Release

1. Ensure that the UNRELEASED section of the [CHANGELOG.md](./CHANGELOG.md) is up to date with all relevant changes.
   See https://github.com/JetBrains/gradle-changelog-plugin for the changelog format.
2. Update the `pluginVersion` within [gradle.properties](./gradle.properties).
3. Every build creates a draft release that can be found
   here https://github.com/oxc-project/oxc-intellij-plugin/releases. These are all release candidates.
4. When ready to release, go to the current draft release and publish it. This will
   run [release.yml](./.github/workflows/release.yml) which will automatically create a PR to move the changes from the
   UNRELEASED section of the changelog to the appropriate version.
