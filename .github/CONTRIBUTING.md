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
