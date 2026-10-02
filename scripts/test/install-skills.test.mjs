import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, cpSync, writeFileSync, readFileSync, existsSync, rmSync, symlinkSync, readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, delimiter } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const source = fileURLToPath(new URL('../../', import.meta.url));
const revision = 'b8e1133597d08eb41ac0d00d5d9555af8c1ba895';
function setup(t) {
  const root = mkdtempSync(join(tmpdir(), 'mod-skills-')); t.after(() => rmSync(root, { recursive: true, force: true }));
  cpSync(join(source, 'scripts'), join(root, 'scripts'), { recursive: true });
  const bin = join(root, 'fake-bin'); mkdirSync(bin);
  writeFileSync(join(bin, 'pnpm'), `#!/usr/bin/env node\nconst fs=require('node:fs'), path=require('node:path'); const args=process.argv.slice(2);fs.appendFileSync('commands.jsonl',JSON.stringify(args)+'\\n'); if(process.env.NO_SKILL==='1')process.exit(0);fs.writeFileSync('skills-lock.json','{}');for(const key of ['cacheDir','storeDir','stateDir']){const dir=args.find(arg=>arg.startsWith('--config.'+key+'=')).split('=').slice(1).join('=');fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,'marker'),'written');} const name=args[args.indexOf('--skill')+1]; const dest=path.join('.agents/skills',name);fs.mkdirSync(dest,{recursive:true});fs.writeFileSync(path.join(dest,'SKILL.md'),'---\\nname: '+name+'\\ndescription: Test\\n---\\n');fs.mkdirSync(path.join(dest,'references'));fs.writeFileSync(path.join(dest,'references','api.md'),'reference');\n`, { mode: 0o755 });
  writeFileSync(join(bin, 'git'), `#!/usr/bin/env node\nconst fs=require('node:fs'),path=require('node:path');const args=process.argv.slice(2);if(args.includes('rev-parse'))console.log('${revision}');if(args[0]==='init'){const dir=args.at(-1);fs.mkdirSync(path.join(dir,'skills/neoforge-modding'),{recursive:true});fs.mkdirSync(path.join(dir,'skills/modrinth-api'),{recursive:true});for(const name of ['neoforge-modding','modrinth-api'])fs.writeFileSync(path.join(dir,'skills',name,'SKILL.md'),'skill');}\n`, { mode: 0o755 });
  return { root, env: { ...process.env, PATH: bin + delimiter + process.env.PATH } };
}
function run(context, args = [], extra = {}) { return spawnSync('bash', [join(context.root, 'scripts/install-skills.sh'), ...args], { cwd: tmpdir(), env: { ...context.env, ...extra }, encoding: 'utf8' }); }
test('installs pinned local skills and references from a different cwd with no global flag', (t) => {
  const c = setup(t); const result = run(c); assert.equal(result.status, 0, result.stderr);
  for (const name of ['neoforge-modding','modrinth-api']) assert.ok(existsSync(join(c.root, '.agents/skills',name,'references/api.md')));
  const commands=readFileSync(join(c.root,'commands.jsonl'),'utf8').trim().split('\n').map(JSON.parse);
  assert.equal(commands.length,2); assert.ok(commands.every((args)=>!args.includes('-g')&&!args.includes('--global')));
  const record=JSON.parse(readFileSync(join(c.root,'.local/skills-installation.json'),'utf8')); assert.equal(record['neoforge-modding'].revision,revision);
});
test('dry run and list do not create directories or execute commands', (t) => {
  const c=setup(t);const before=readdirSync(c.root); for(const flag of ['--dry-run','--list']) assert.equal(run(c,[flag]).status,0);
  assert.deepEqual(readdirSync(c.root),before);
});
test('existing skill is preserved and unknown revision reported', (t) => {
  const c=setup(t); const skill=join(c.root,'.agents/skills/neoforge-modding');mkdirSync(skill,{recursive:true});writeFileSync(join(skill,'SKILL.md'),'custom');
  const result=run(c);assert.equal(result.status,0,result.stderr);assert.match(result.stdout,/unknown revision/);assert.equal(readFileSync(join(skill,'SKILL.md'),'utf8'),'custom');
});
for (const [label, value] of [['revision', revision.replace(/.$/,'x')], ['path', '../outside']]) {
  test(`invalid manifest ${label} refuses all installations`, (t) => {
    const c=setup(t);let text=readFileSync(join(c.root,'scripts/skills.tsv'),'utf8');text+=`core\tbad-skill\tsiredvin/minecraft-modding-skills\t${label==='revision'?value:revision}\t${label==='path'?value:'skills/bad-skill'}\n`;writeFileSync(join(c.root,'scripts/skills.tsv'),text);
    assert.notEqual(run(c).status,0); assert.ok(!existsSync(join(c.root,'.agents'))); assert.ok(!existsSync(join(c.root,'commands.jsonl')));
  });
}
test('symlink installation root is rejected before fetching', (t) => {
  const c=setup(t);mkdirSync(join(c.root,'elsewhere'));symlinkSync(join(c.root,'elsewhere'),join(c.root,'.agents'));
  assert.notEqual(run(c).status,0);assert.ok(!existsSync(join(c.root,'commands.jsonl')));assert.deepEqual(readdirSync(join(c.root,'elsewhere')),[]);
});
test('CLI zero exit without SKILL.md is a failure', (t) => {
  const c=setup(t);assert.notEqual(run(c,[],{NO_SKILL:'1'}).status,0);assert.ok(!existsSync(join(c.root,'.local/skills-installation.json')));
});

for (const output of ['skills-lock.json', '.local/pnpm-cache', '.local/pnpm-store', '.local/pnpm-state']) {
  test(`symlink output ${output} is rejected without writes outside the repository`, (t) => {
    const c = setup(t); const outside = mkdtempSync(join(tmpdir(), 'mod-skills-outside-'));
    t.after(() => rmSync(outside, { recursive: true, force: true }));
    const target = output.endsWith('.json') ? join(outside, 'lock.json') : outside;
    if (output.endsWith('.json')) writeFileSync(target, '{"preserve":true}');
    mkdirSync(join(c.root, '.local'), { recursive: true }); symlinkSync(target, join(c.root, output));
    const result = run(c);
    assert.notEqual(result.status, 0, result.stdout);
    assert.ok(!existsSync(join(c.root, 'commands.jsonl')));
    assert.deepEqual(readdirSync(outside), output.endsWith('.json') ? ['lock.json'] : []);
    if (output.endsWith('.json')) assert.equal(readFileSync(target, 'utf8'), '{"preserve":true}');
  });
}
test('pnpm internal cache links remain usable for repeated installation', (t) => {
  const c = setup(t); const cache = join(c.root, '.local/pnpm-cache');
  mkdirSync(join(cache, 'packages/dependency'), { recursive: true });
  mkdirSync(join(cache, 'node_modules'), { recursive: true });
  symlinkSync(join(cache, 'packages/dependency'), join(cache, 'node_modules/dependency'));
  const result = run(c); assert.equal(result.status, 0, result.stderr);
  assert.equal(run(c).status, 0);
});
test('nested pnpm cache links escaping all output roots are refused', (t) => {
  const c = setup(t); const outside = mkdtempSync(join(tmpdir(), 'pnpm-outside-'));
  t.after(() => rmSync(outside, { recursive: true, force: true }));
  mkdirSync(join(c.root, '.local/pnpm-cache'), { recursive: true });
  symlinkSync(outside, join(c.root, '.local/pnpm-cache/nested'));
  assert.notEqual(run(c).status, 0);
  assert.deepEqual(readdirSync(outside), []);
  assert.ok(!existsSync(join(c.root, 'commands.jsonl')));
});
