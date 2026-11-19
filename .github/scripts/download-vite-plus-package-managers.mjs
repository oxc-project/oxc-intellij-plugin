// Runs each package manager fixture's vp once, so that the tests' Vite+ servers start at once.
//
// The first time vp runs in a workspace, it downloads the package manager named in the workspace's
// `packageManager` field into its own home folder, and only then starts the language server. On a fresh
// runner that takes longer than the tests wait, and vp ignores the shutdown until the download ends, so
// each test leaves a server running. `vp lint --lsp` with no input downloads it, then exits.
//
// The Yarn PnP fixture needs the same Yarn as the Yarn fixture, so it isn't run here.
//
// Usage: node download-vite-plus-package-managers.mjs [folder holding the fixtures]

import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';

const fixtures = path.resolve(process.argv[2] || 'src/test/testData/package-managers');
const managers = ['npm', 'yarn', 'pnpm', 'bun'];
const timeoutMs = 5 * 60 * 1000;

/** npm and Yarn hoist vite-plus to the workspace root, and pnpm and Bun keep it in the package. */
function findVp(folder, root) {
  for (let dir = folder; ; dir = path.dirname(dir)) {
    const vp = path.join(dir, 'node_modules', 'vite-plus', 'bin', 'vp');
    if (existsSync(vp)) return vp;
    if (dir === root) throw new Error(`No vite-plus between ${folder} and ${root}. Install the fixture first.`);
  }
}

for (const manager of managers) {
  const root = path.join(fixtures, manager);
  const folder = path.join(root, 'packages', 'vite');
  const vp = findVp(folder, root);
  const start = Date.now();
  const result = spawnSync(process.execPath, [vp, 'lint', '--lsp'], {
    cwd: folder,
    stdio: ['ignore', 'ignore', 'inherit'],
    timeout: timeoutMs,
  });
  const seconds = ((Date.now() - start) / 1000).toFixed(1);
  if (result.error) throw new Error(`vp failed in ${folder} after ${seconds}s`, { cause: result.error });
  if (result.status !== 0) throw new Error(`vp exited with ${result.status ?? result.signal} in ${folder} after ${seconds}s`);
  console.log(`${manager}: vp ready in ${seconds}s`);
}
