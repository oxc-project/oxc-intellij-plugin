#!/usr/bin/env node
// A scriptable stand-in for the Oxlint/Oxfmt language servers, used to test how the plugin handles
// servers that crash, hang or fail. Its behavior is read on every start from `fake-lsp.json` next
// to this script:
//
//   crashOn:     <method> exit with code 1 when that request or notification arrives
//   hang:        [method] never answer these requests
//   fail:        [method] answer these requests with a JSON-RPC error
//
// Every message received is appended to `fake-lsp.log` as `<method>` on its own line.
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

// There is deliberately no option to exit before answering `initialize`: the platform then keeps
// waiting for the answer on a pooled thread that never finishes, which the test fixture reports
// as a leaked thread. Crash on `initialized` instead to fail during startup.

const capabilities = {
  textDocumentSync: 1,
  documentFormattingProvider: true,
  codeActionProvider: { codeActionKinds: ["quickfix", "source.fixAll.oxc", "source.fixAllDangerous.oxc"] },
  diagnosticProvider: { interFileDependencies: false, workspaceDiagnostics: false },
};

function send(message) {
  const body = JSON.stringify({ jsonrpc: "2.0", ...message });
  process.stdout.write(`Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`);
}

function handle(message) {
  fs.appendFileSync(logFile, `${message.method ?? "response"}\n`);
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
      send({ id: message.id, result: [] });
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
