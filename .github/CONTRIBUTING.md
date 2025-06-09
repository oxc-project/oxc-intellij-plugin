### Running Tests

Tests can be executed with `./gradlew check`. Prior to running tests, you'll need to execute `npm install` within each
of the test directories found at `src/test/testData`.

If you fail to execute `npm install` within a test directory, you'll typically receive an exception like the below.

```
Caused by: java.io.FileNotFoundException: /private/var/folders/y9/7_0wzf515vsdjc62stxjngnc0000gn/T/unitTest_rootFileHighlighting_2yF2NjR6MfHBbuoM6d9V6JBZdFT/unitTest13365413598591665525/node_modules/oxlint/bin/oxc_language_server (No such file or directory)
	at java.base/java.io.FileInputStream.open0(Native Method)
	at java.base/java.io.FileInputStream.open(FileInputStream.java:213)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:152)
	at com.github.oxc.project.oxcintellijplugin.OxcTargetRunBuilder.getBuilder(OxcTargetRun.kt:99)
	at com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerDescriptor.<init>(OxcLspServerDescriptor.kt:24)
	at com.github.oxc.project.oxcintellijplugin.lsp.OxcLspServerSupportProvider.fileOpened(OxcLspServerSupportProvider.kt:41)
```
