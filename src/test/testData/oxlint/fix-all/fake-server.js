#!/usr/bin/env node
// A language server with scripted answers, for what a real one can't be made to do on demand. It reads
// its script from fake-server.json in its working directory, and writes each request's method and params
// to requests.log there.
//
// Script fields, which are all optional:
//   cached: code actions to answer with straight away
//   afterDiagnostic: code actions to answer with once diagnostics have been pulled after a code action
//     request, because the platform can also pull them by itself before that
//   hang: true to never answer a code action or formatting request
//   initializeDelayMs: how long to wait before answering initialize, to keep the server starting
//   formatting: text edits to answer a formatting request with, as { "start": [line, character],
//     "end": [line, character], "newText" }
//
// A code action is { "title", "isPreferred", "insert" }, where insert is text to add at the file's start.
"use strict";

const fs = require("fs");

const script = () => JSON.parse(fs.readFileSync("fake-server.json", "utf8"));
let codeActionRequested = false;
let diagnosticsPulled = false;

function send(message) {
  const body = JSON.stringify({ jsonrpc: "2.0", ...message });
  process.stdout.write(`Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`);
}

function later(delayMs, message) {
  if (delayMs) setTimeout(send, delayMs, message);
  else send(message);
}

function toCodeAction(action, uri) {
  const start = { line: 0, character: 0 };
  return {
    title: action.title,
    kind: "source.fixAll.oxc",
    isPreferred: action.isPreferred,
    edit: { changes: { [uri]: [{ range: { start, end: start }, newText: action.insert }] } },
  };
}

function toTextEdit({ start, end, newText }) {
  return {
    range: {
      start: { line: start[0], character: start[1] },
      end: { line: end[0], character: end[1] },
    },
    newText,
  };
}

function handle(message) {
  if (message.id === undefined || message.method === undefined) return;
  fs.appendFileSync("requests.log", `${JSON.stringify({ method: message.method, params: message.params })}\n`);

  switch (message.method) {
    case "initialize":
      // Some tests start this server without a script, so a missing one means no delay.
      later(fs.existsSync("fake-server.json") ? script().initializeDelayMs : undefined, {
        id: message.id,
        result: {
          capabilities: {
            textDocumentSync: { openClose: true, change: 1 },
            codeActionProvider: true,
            documentFormattingProvider: true,
            diagnosticProvider: { interFileDependencies: false, workspaceDiagnostics: false },
          },
        },
      });
      break;
    case "textDocument/diagnostic":
      diagnosticsPulled = codeActionRequested;
      send({ id: message.id, result: { kind: "full", items: [] } });
      break;
    case "textDocument/codeAction": {
      const { cached, afterDiagnostic, hang } = script();
      if (hang) break;
      const actions = cached ?? (diagnosticsPulled ? afterDiagnostic : []) ?? [];
      send({ id: message.id, result: actions.map((it) => toCodeAction(it, message.params.textDocument.uri)) });
      codeActionRequested = true;
      break;
    }
    case "textDocument/formatting": {
      const { formatting, hang } = script();
      if (hang) break;
      send({ id: message.id, result: (formatting ?? []).map(toTextEdit) });
      break;
    }
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
    const length = Number(/Content-Length: (\d+)/i.exec(buffer.toString("ascii", 0, headerEnd))[1]);
    if (buffer.length < headerEnd + 4 + length) return;
    const body = buffer.toString("utf8", headerEnd + 4, headerEnd + 4 + length);
    buffer = buffer.subarray(headerEnd + 4 + length);
    handle(JSON.parse(body));
  }
});
process.stdin.on("end", () => process.exit(0));
