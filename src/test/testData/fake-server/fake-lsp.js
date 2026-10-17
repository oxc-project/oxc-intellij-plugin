#!/usr/bin/env node
// A scriptable stand-in for the Oxlint/Oxfmt language servers, used to test how the plugin handles
// servers that crash, hang or fail. Its behavior is read on every start from `fake-lsp.json` next
// to this script:
//
//   crashOn:     <method> exit with code 1 when that request or notification arrives
//   hang:        [method] never answer these requests
//   fail:        [method] answer these requests with a JSON-RPC error
//   capabilities: {...}  extra server capabilities to advertise, merged over the defaults
//   fixAllText:  <text>  answer code action requests with a preferred "Fix All" action that
//                        replaces the whole document with this text
//
// Every message received is appended to `fake-lsp.log` as `<method>` on its own line. The file
// URIs the server is given are appended to `fake-lsp-uris.log` as `<where> <uri>`: the root and
// `workspaceUri` initialization options of `initialize`, and the document of `textDocument/*`.
const fs = require("fs");
const path = require("path");

const behavior = (() => {
  try {
    return JSON.parse(fs.readFileSync(path.join(__dirname, "fake-lsp.json"), "utf8"));
  } catch {
    return {};
  }
})();
const logFile = path.join(__dirname, "fake-lsp.log");
const urisFile = path.join(__dirname, "fake-lsp-uris.log");
const documents = new Map();

// There is deliberately no option to exit before answering `initialize`: the platform then keeps
// waiting for the answer on a pooled thread that never finishes, which the test fixture reports
// as a leaked thread. Crash on `initialized` instead to fail during startup.

const capabilities = {
  textDocumentSync: 1,
  documentFormattingProvider: true,
  codeActionProvider: { codeActionKinds: ["quickfix", "source.fixAll.oxc", "source.fixAllDangerous.oxc"] },
  diagnosticProvider: { interFileDependencies: false, workspaceDiagnostics: false },
  ...behavior.capabilities,
};

function send(message) {
  const body = JSON.stringify({ jsonrpc: "2.0", ...message });
  process.stdout.write(`Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`);
}

function logUris(message) {
  const params = message.params ?? {};
  const uris = [];
  if (message.method === "initialize") {
    uris.push(["rootUri", params.rootUri]);
    for (const option of [params.initializationOptions ?? []].flat()) {
      uris.push(["workspaceUri", option?.workspaceUri]);
    }
  } else if (params.textDocument?.uri) {
    uris.push([message.method, params.textDocument.uri]);
  }
  for (const [where, uri] of uris) {
    fs.appendFileSync(urisFile, `${where} ${uri}\n`);
  }
}

function trackDocument(message) {
  const params = message.params ?? {};
  if (message.method === "textDocument/didOpen") {
    documents.set(params.textDocument.uri, params.textDocument.text);
  } else if (message.method === "textDocument/didChange") {
    documents.set(params.textDocument.uri, params.contentChanges.at(-1).text);
  }
}

/** A preferred "Fix All" action replacing the whole document with `behavior.fixAllText`. */
function fixAll(uri) {
  const lines = (documents.get(uri) ?? "").split("\n");
  const end = { line: lines.length - 1, character: lines.at(-1).length };
  const edit = { range: { start: { line: 0, character: 0 }, end }, newText: behavior.fixAllText };
  return [{ title: "Fix all", kind: "source.fixAll.oxc", isPreferred: true, edit: { changes: { [uri]: [edit] } } }];
}

function handle(message) {
  fs.appendFileSync(logFile, `${message.method ?? "response"}\n`);
  logUris(message);
  trackDocument(message);
  if (message.method === behavior.crashOn) {
    process.exit(1);
  }
  if (message.method === "exit") {
    process.exit(0);
  }
  if (message.id === undefined || message.method === undefined) {
    return; // A notification, or a response to one of our requests.
  }
  if ((behavior.hang ?? []).includes(message.method)) {
    return;
  }
  if ((behavior.fail ?? []).includes(message.method)) {
    send({ id: message.id, error: { code: -32603, message: `fake failure in ${message.method}` } });
    return;
  }
  switch (message.method) {
    case "initialize":
      send({ id: message.id, result: { capabilities } });
      break;
    case "textDocument/diagnostic":
      send({ id: message.id, result: { kind: "full", items: [] } });
      break;
    case "textDocument/codeAction":
      send({ id: message.id, result: behavior.fixAllText === undefined ? [] : fixAll(message.params.textDocument.uri) });
      break;
    default:
      send({ id: message.id, result: null });
  }
}

let buffer = Buffer.alloc(0);
process.stdin.on("data", (chunk) => {
  buffer = Buffer.concat([buffer, chunk]);
  for (;;) {
    const headerEnd = buffer.indexOf("\r\n\r\n");
    if (headerEnd < 0) return;
    const length = Number(/Content-Length: (\d+)/i.exec(buffer.subarray(0, headerEnd).toString())[1]);
    const start = headerEnd + 4;
    if (buffer.length < start + length) return;
    const body = buffer.subarray(start, start + length).toString();
    buffer = buffer.subarray(start + length);
    handle(JSON.parse(body));
  }
});
process.stdin.on("end", () => process.exit(0));
