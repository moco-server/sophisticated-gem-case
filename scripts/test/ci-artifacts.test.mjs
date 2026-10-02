import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { collectModJar } from '../collect-mod-jar.mjs';
import { checkGameTestResult } from '../check-gametest-result.mjs';
function fixture(t) { const root=mkdtempSync(join(tmpdir(),'mod-ci-'));t.after(()=>rmSync(root,{recursive:true,force:true}));mkdirSync(join(root,'neoforge/build/libs'),{recursive:true});return root; }
test('collects one normal jar without a fixed version and excludes classifiers',(t)=>{
  const root=fixture(t);for(const name of ['example-1.2.3.jar','example-1.2.3-sources.jar','example-1.2.3-gametests.jar'])writeFileSync(join(root,'neoforge/build/libs',name),'jar');
  collectModJar(root);assert.deepEqual(readdirSync(join(root,'.local/artifact')),['example-1.2.3.jar']);
});
test('refuses zero or multiple normal jars',(t)=>{
  const root=fixture(t);assert.throws(()=>collectModJar(root));
  for(const name of ['one.jar','two.jar'])writeFileSync(join(root,'neoforge/build/libs',name),'jar');assert.throws(()=>collectModJar(root));
});
test('accepts positive passing GameTest counts and refuses missing zero failed logs',()=>{
  assert.doesNotThrow(()=>checkGameTestResult('All 2 required tests passed :)'));
  for(const text of ['', 'All 0 required tests passed :)', '2 required tests failed :('])assert.throws(()=>checkGameTestResult(text));
});
