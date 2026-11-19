// Reads an Oxfmt config the way Oxfmt does, and prints the result as one line of JSON.
//
//   node load-oxfmt-config.mjs javascript <config>
//   node load-oxfmt-config.mjs vite-plus <vite-plus folder> <config>...
//
// `javascript` takes the config's default export, as Oxfmt's `loadJsConfig` does. `vite-plus` takes the
// `fmt` field of what `vite-plus`'s `resolveConfig` gives, as Oxfmt's `loadVitePlusConfig` does, and
// moves on to the next config when there's no `fmt` field, as the server's search does.
//
// The result is one of:
//   { "kind": "loaded", "index": <the config's position>, "config": { ... } }
//   { "kind": "missing" }
//   { "kind": "node-version", "index": ..., "version": "v20.0.0", "message": "..." }
//   { "kind": "error", "index": ..., "message": "...", "detail": "..." }
import { readFileSync } from "node:fs";
import { extname, join } from "node:path";
import { pathToFileURL } from "node:url";

// Anything that the config or its plugins print goes to stderr, so that stdout only has the result.
const writeResult = process.stdout.write.bind(process.stdout);
process.stdout.write = process.stderr.write.bind(process.stderr);

const TYPESCRIPT_EXTENSIONS = new Set([".ts", ".mts", ".cts"]);

function finish(result) {
  writeResult(`${JSON.stringify(result)}\n`, () => process.exit(0));
}

function isObject(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

// Oxfmt's own check for a Node.js that can't load TypeScript.
function isUnknownFileExtension(error) {
  if (error?.code === "ERR_UNKNOWN_FILE_EXTENSION") return true;
  const message = error?.message;
  return typeof message === "string" && /unknown(?: or unsupported)? file extension/i.test(message);
}

function failure(error, index, path) {
  const message = error instanceof Error ? error.message : String(error);
  if (path !== undefined && TYPESCRIPT_EXTENSIONS.has(extname(path).toLowerCase()) && isUnknownFileExtension(error)) {
    return { kind: "node-version", index, version: process.version, message };
  }
  return { kind: "error", index, message, detail: error instanceof Error ? (error.stack ?? message) : message };
}

// The file that `import "vite-plus"` would load, from the package's `exports`.
function entryPoint(folder) {
  const manifest = JSON.parse(readFileSync(join(folder, "package.json"), "utf8"));
  let target = manifest.exports;
  if (isObject(target) && "." in target) target = target["."];
  while (isObject(target)) target = target.node ?? target.import ?? target.default;
  return { file: join(folder, typeof target === "string" ? target : (manifest.module ?? manifest.main ?? "index.js")), version: manifest.version };
}

async function loadJavaScript(path) {
  const module = await import(pathToFileURL(path).href);
  if (module.default === undefined) throw new Error("Configuration file has no default export.");
  if (!isObject(module.default)) throw new Error("Configuration file must have a default export that is an object.");
  return { kind: "loaded", index: 0, config: module.default };
}

async function loadVitePlus(folder, paths, onConfig) {
  const entry = entryPoint(folder);
  // `vp` sets this for the tools it runs, and `vite-plus` can check it while it reads the config.
  process.env.VP_VERSION ??= entry.version;
  const { resolveConfig } = await import(pathToFileURL(entry.file).href);
  for (const [index, path] of paths.entries()) {
    onConfig(index);
    const config = await resolveConfig({ configFile: path }, "build");
    if (!("fmt" in config)) continue;
    if (!isObject(config.fmt)) throw new Error("The `fmt` field in the default export must be an object.");
    return { kind: "loaded", index, config: config.fmt };
  }
  return { kind: "missing" };
}

const [mode, ...rest] = process.argv.slice(2);
const paths = mode === "vite-plus" ? rest.slice(1) : rest;
let current = 0;
try {
  finish(mode === "vite-plus" ? await loadVitePlus(rest[0], paths, (index) => (current = index)) : await loadJavaScript(paths[0]));
} catch (error) {
  finish(failure(error, current, paths[current]));
}
