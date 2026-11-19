# Contributing

## Running the plugin

Start an IDE with the plugin installed, running the backend and frontend as separate processes in split mode:

```bash
./gradlew :runIdeSplitMode
```

In IntelliJ IDEA, the **Run IDE with Plugin (Split Mode)** run configuration runs the same Gradle task.

Start an IDE with the plugin installed, running as a single process without split mode:

```bash
./gradlew :runIdeMonolith
```

In IntelliJ IDEA, the **Run IDE with Plugin (Monolith)** run configuration runs the same Gradle task.

## Running the tests

The highlighting tests start a real language server from their fixture's `node_modules`, so they need Node on your
`PATH` and the fixture's dependencies installed:

```bash
npm ci --prefix src/test/testData/oxlint/highlighting/no-config
```

```bash
./gradlew check
```

To run the tests on Windows with Node in WSL, set `OXC_TEST_WSL_DISTRO` to an installed distribution. It needs Node at
`OXC_TEST_WSL_NODE` (default `/usr/local/bin/node`), and `npm ci` run inside WSL in the fixture folder. CI does this in
its `Test (WSL)` jobs. New tests that use Node should extend `OxcFixtureTestCase` so that they run there too.

Test projects are created on the Windows side by default. To store them inside WSL, set `OXC_TEST_TMPDIR` to an existing
folder in the distribution, such as `\\wsl.localhost\Ubuntu-24.04\tmp\oxc-tests`.
