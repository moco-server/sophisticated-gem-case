import { existsSync, lstatSync, realpathSync, readFileSync, mkdirSync, writeFileSync, renameSync, readdirSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { pnpmCommand } from './pnpm-command.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const cliVersion = '1.7.0';
function protectedPath(path) {
  let current = root;
  for (const part of relative(root, path).split(sep).filter(Boolean)) {
    if (part === '..') throw new Error('Path escapes repository');
    current = join(current, part);
    try { if (lstatSync(current).isSymbolicLink()) throw new Error(`Symlink refused: ${current}`); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
}
function command(name, args, cwd = root) {
  const invocation = name === 'pnpm' ? pnpmCommand(args) : { file: name, args };
  const result = spawnSync(invocation.file, invocation.args, { cwd, stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8', env: { ...process.env, GIT_TERMINAL_PROMPT: '0' } });
  if (result.error || result.status !== 0) throw new Error(`${name} failed: ${result.error?.message ?? result.stderr}`);
  if (result.stderr) process.stderr.write(result.stderr);
  return result.stdout;
}
function inspectTree(path) {
  protectedPath(path);
  for (const name of readdirSync(path)) {
    const child = join(path,name); protectedPath(child);
    if (lstatSync(child).isDirectory()) inspectTree(child);
  }
}
function inspectCache(path, roots) {
  for (const name of readdirSync(path)) {
    const child = join(path, name);
    if (lstatSync(child).isSymbolicLink()) {
      const destination = realpathSync(child);
      if (!roots.some((root) => destination === root || destination.startsWith(root + sep))) {
        throw new Error(`Cache symlink escapes output roots: ${child}`);
      }
    } else if (lstatSync(child).isDirectory()) inspectCache(child, roots);
  }
}
function install() {
  const args = process.argv.slice(2); let mode = 'install'; let group = 'core';
  for (const arg of args) {
    if (arg === '--list' || arg === '--dry-run') mode = arg.slice(2);
    else if (arg === 'core' || arg === 'all') group = arg;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  const entries = []; const names = new Set();
  for (const line of readFileSync(join(root,'scripts/skills.tsv'),'utf8').split(/\r?\n/)) {
    if (!line.trim() || line.startsWith('#')) continue;
    const fields = line.split('\t');
    if (fields.length !== 5) throw new Error('Manifest must have five tab-separated fields');
    const [entryGroup,name,source,revision,path] = fields;
    if (entryGroup !== 'core' || !/^[a-z0-9]+(-[a-z0-9]+)*$/.test(name) || names.has(name) ||
      !/^[A-Za-z0-9_-]+\/[A-Za-z0-9_.-]+$/.test(source) || !/^[a-f0-9]{40}$/.test(revision) ||
      !/^[A-Za-z0-9_-]+(\/[A-Za-z0-9_-]+)+$/.test(path) || path.split('/').at(-1) !== name) throw new Error(`Invalid manifest entry: ${name}`);
    names.add(name); entries.push({ group:entryGroup,name,source,revision,path });
  }
  const recordPath = join(root,'.local/skills-installation.json'); protectedPath(recordPath);
  protectedPath(join(root,'.agents/skills')); protectedPath(join(root,'.local/skills-sources'));
  // skills CLIとpnpm自身が書き込む場所も、fetchや導入より前に検証する。
  protectedPath(join(root, 'skills-lock.json'));
  const caches = ['pnpm-cache', 'pnpm-store', 'pnpm-state'].map((name) => join(root, '.local', name));
  caches.forEach(protectedPath);
  // pnpm自身の内部リンクは許可し、3つの出力rootを離れるリンクは拒否する。
  const cacheRoots = caches.map((path) => existsSync(path) ? realpathSync(path) : path);
  for (const cache of caches) if (existsSync(cache)) inspectCache(cache, cacheRoots);
  let record = existsSync(recordPath) ? JSON.parse(readFileSync(recordPath,'utf8')) : {};
  for (const entry of entries) protectedPath(join(root,'.agents/skills',entry.name));
  for (const entry of entries.filter((entry)=>group==='all'||entry.group===group)) {
    const url = `https://github.com/${entry.source}/tree/${entry.revision}/${entry.path}`;
    if (mode !== 'install') { console.log(`${entry.name}\t${url}\t${mode}: no changes`); continue; }
    const destination = join(root,'.agents/skills',entry.name);
    if (existsSync(destination)) {
      const current = record[entry.name]?.revision ?? 'unknown revision';
      console.log(`Skip existing ${entry.name}: ${current}${current!==entry.revision?' (does not match pinned revision)':''}; no overwrite`);
      continue;
    }
    const checkout = join(root,'.local/skills-sources',entry.source.replaceAll('/','--')+'--'+entry.revision);
    protectedPath(checkout);
    if (!existsSync(checkout)) {
      mkdirSync(dirname(checkout),{recursive:true});
      command('git',['init','--quiet',checkout]);
      command('git',['-C',checkout,'fetch','--quiet','--depth=1',`https://github.com/${entry.source}.git`,entry.revision]);
      command('git',['-C',checkout,'checkout','--quiet','--detach','FETCH_HEAD']);
    }
    const actual = command('git',['-C',checkout,'rev-parse','HEAD']).trim();
    if (actual !== entry.revision) throw new Error(`Source revision mismatch for ${entry.name}: ${actual}`);
    const source = join(checkout,entry.path); inspectTree(source);
    if (!existsSync(join(source,'SKILL.md'))) throw new Error(`Missing source SKILL.md for ${entry.name}`);
    console.log(`Installing ${entry.name} at ${actual}`);
    const output = command('pnpm',[`--config.stateDir=${join(root,'.local/pnpm-state')}`,`--config.cacheDir=${join(root,'.local/pnpm-cache')}`,`--config.storeDir=${join(root,'.local/pnpm-store')}`,'--config.ignoreScripts=true','dlx',`skills@${cliVersion}`,'add',source,'--skill',entry.name,'--agent','codex','--copy','--yes']);
    process.stdout.write(output);
    protectedPath(join(destination,'SKILL.md'));
    if (!existsSync(join(destination,'SKILL.md'))) throw new Error(`Installation did not produce ${entry.name}/SKILL.md`);
    inspectTree(destination);
    record[entry.name] = { ...entry, cliVersion };
    mkdirSync(dirname(recordPath),{recursive:true});
    const temporary = recordPath+'.tmp'; protectedPath(temporary);
    writeFileSync(temporary,JSON.stringify(record,null,2)+'\n'); renameSync(temporary,recordPath);
  }
}
try { install(); } catch (error) { console.error(error.message); process.exitCode=1; }
