import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

test('Windows pnpm cmd shims use Node with literal arguments in paths containing spaces', async (t) => {
  const { pnpmCommand } = await import('../pnpm-command.mjs');
  const root = mkdtempSync(join(tmpdir(), 'pnpm command '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const entry = join(root, 'node_modules/pnpm/bin/pnpm.cjs');
  mkdirSync(join(root, 'node_modules/pnpm/bin'), { recursive: true });
  writeFileSync(join(root, 'pnpm.cmd'), '@echo off');
  writeFileSync(entry, 'console.log(JSON.stringify(process.argv.slice(2)));');
  const args = ['path with spaces', '$&', 'quote"here', 'semi;colon'];
  const command = pnpmCommand(args, { platform: 'win32', path: root, execPath: process.execPath });
  const result = spawnSync(command.file, command.args, { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(JSON.parse(result.stdout), args);
});
test('Windows node_modules bin shims and Corepack have supported JS entry points', async (t) => {
  const { pnpmCommand } = await import('../pnpm-command.mjs');
  const root = mkdtempSync(join(tmpdir(), 'pnpm layouts '));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  for (const [directory, script] of [
    ['node_modules/.bin', 'node_modules/pnpm/bin/pnpm.cjs'],
    ['pnpm12/node_modules/.bin', 'pnpm12/node_modules/pnpm/bin/pnpm.mjs'],
    ['node', 'node/node_modules/corepack/dist/pnpm.js'],
  ]) {
    mkdirSync(join(root, directory), { recursive: true });
    mkdirSync(join(root, script, '..'), { recursive: true });
    writeFileSync(join(root, directory, 'pnpm.cmd'), '@echo off');
    writeFileSync(join(root, script), '');
    const command = pnpmCommand(['--version'], { platform: 'win32', path: join(root, directory), execPath: process.execPath });
    assert.equal(command.file, process.execPath);
    assert.deepEqual(command.args, [join(root, script), '--version']);
  }
});
