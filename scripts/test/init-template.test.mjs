import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync, readFileSync, writeFileSync, rmSync, readdirSync, lstatSync, symlinkSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const source = fileURLToPath(new URL('../../', import.meta.url));
function sandbox(t) {
  const dir = mkdtempSync(join(tmpdir(), 'mod-init-'));
  cpSync(join(source, 'scripts/test/fixtures/uninitialized-template'), dir, { recursive: true });
  mkdirSync(join(dir, 'scripts'));
  cpSync(join(source, 'scripts/init-template.mjs'), join(dir, 'scripts/init-template.mjs'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  return dir;
}
const options = ['--mod-id', 'test_gem', '--name', '宝石 "ケース"', '--package', 'org.mocoserver.testgem', '--group', 'org.mocoserver.mods', '--author', '作者 "A"', '--repo-url', 'https://github.com/moco-server/test-gem'];
function run(dir, args = options) { return spawnSync(process.execPath, [join(dir, 'scripts/init-template.mjs'), ...args], { cwd: tmpdir(), encoding: 'utf8' }); }
function snapshot(dir) {
  const output = {};
  function walk(path) {
    for (const entry of readdirSync(path)) {
      const file = join(path, entry);
      if (lstatSync(file).isDirectory()) walk(file);
      else output[file.slice(dir.length)] = lstatSync(file).isSymbolicLink() ? 'symlink' : readFileSync(file).toString('base64');
    }
  }
  walk(dir);
  return output;
}
test('renames source namespaces metadata and documents without touching unknown files', (t) => {
  const dir = sandbox(t);
  writeFileSync(join(dir, 'untouched.txt'), 'mod_template org.mocoserver.modtemplate');
  const result = run(dir);
  assert.equal(result.status, 0, result.stderr);
  const code = readFileSync(join(dir, 'neoforge/src/main/kotlin/org/mocoserver/testgem/ModTemplate.kt'), 'utf8');
  assert.match(code, /package org.mocoserver.testgem/);
  assert.match(code, /const val ID = "test_gem"/);
  assert.ok(existsSync(join(dir, 'neoforge/src/generated/resources/data/test_gem/recipe/sample_token.json')));
  assert.ok(!existsSync(join(dir, 'neoforge/src/main/kotlin/org/mocoserver/modtemplate/ModTemplate.kt')));
  assert.match(readFileSync(join(dir, 'settings.gradle.kts'), 'utf8'), /rootProject.name = \"test_gem\"/);
  const properties = readFileSync(join(dir, 'gradle.properties'), 'utf8');
  assert.match(properties, /mod_name=\\u5b9d\\u77f3\\ "\\u30b1\\u30fc\\u30b9"/);
  assert.match(properties, /mod_group_id=org.mocoserver.mods/);
  assert.equal(readFileSync(join(dir, 'untouched.txt'), 'utf8'), 'mod_template org.mocoserver.modtemplate');
  assert.equal(JSON.parse(readFileSync(join(dir, '.template-initialized.json'), 'utf8')).modId, 'test_gem');
  for (const path of ['docs/verification.md', '.agents/skills/mod-kotlin/references/coding-conventions.md', '.agents/skills/mod-implement/references/development.md']) {
    assert.doesNotMatch(readFileSync(join(dir, path), 'utf8'), /mod_template/);
  }
});
test('dry run changes no files', (t) => {
  const dir = sandbox(t); const before = snapshot(dir);
  const result = run(dir, [...options, '--dry-run']);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(snapshot(dir), before);
});
for (const [label, flag, value] of [['invalid id', '--mod-id', 'Wrong-ID'], ['path traversal', '--package', '../outside'], ['reserved package', '--package', 'org.class.mod'], ['unsafe URL', '--repo-url', 'file:///tmp/repo']]) {
  test(`${label} is rejected before changes`, (t) => {
    const dir = sandbox(t); const before = snapshot(dir); const args = [...options]; args[args.indexOf(flag) + 1] = value;
    assert.notEqual(run(dir, args).status, 0);
    assert.deepEqual(snapshot(dir), before);
  });
}
test('reinitialization preserves the initialized repository', (t) => {
  const dir = sandbox(t); assert.equal(run(dir).status, 0); const before = snapshot(dir);
  assert.notEqual(run(dir).status, 0); assert.deepEqual(snapshot(dir), before);
});
test('target symlink is rejected without modifying its destination', { skip: process.platform === 'win32' }, (t) => {
  const dir = sandbox(t); const target = join(dir, 'gradle.properties'); const original = readFileSync(target);
  const external = join(dir, 'external.properties'); writeFileSync(external, original); rmSync(target); symlinkSync(external, target);
  const before = snapshot(dir); assert.notEqual(run(dir).status, 0); assert.deepEqual(snapshot(dir), before);
});

test('missing template version is rejected without partial changes', (t) => {
  const dir = sandbox(t); rmSync(join(dir, 'template-version.txt')); const before = snapshot(dir);
  assert.notEqual(run(dir).status, 0); assert.deepEqual(snapshot(dir), before);
});

test('accepted package values containing a placeholder remain literal', (t) => {
  const dir = sandbox(t); const args = [...options];
  args[args.indexOf('--package') + 1] = 'org.mod_template.example';
  assert.equal(run(dir, args).status, 0);
  const code = readFileSync(join(dir, 'neoforge/src/main/kotlin/org/mod_template/example/ModTemplate.kt'), 'utf8');
  assert.match(code, /package org\.mod_template\.example/);
});
test('accepted URL replacement tokens remain literal in documents', (t) => {
  const dir = sandbox(t); const args = [...options];
  const url = 'https://git.example.test/repo/$&'; args[args.indexOf('--repo-url') + 1] = url;
  assert.equal(run(dir, args).status, 0);
  assert.ok(readFileSync(join(dir, 'README.md'), 'utf8').includes(url));
});
