#!/usr/bin/env node
import { existsSync, lstatSync, readFileSync, readdirSync, mkdirSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const originalPackage = 'org.mocoserver.modtemplate';
const originalId = 'mod_template';
const fields = ['mod-id', 'name', 'package', 'group', 'author', 'repo-url'];
const reserved = new Set('abstract as assert boolean break byte case catch char class const continue default do double else enum extends false final finally float for fun goto if implements import in instanceof int interface is long native new null object package private protected public return short static strictfp super switch synchronized this throw throws transient true try typealias typeof val var void volatile when while'.split(' '));
function safePath(path) {
  let current = root;
  for (const part of relative(root, path).split(sep).filter(Boolean)) {
    if (part === '..') throw new Error('Path escapes repository');
    current = join(current, part);
    if (existsSync(current) && lstatSync(current).isSymbolicLink()) throw new Error(`Symlink target refused: ${current}`);
    // 壊れたsymlinkもexistsSyncでは見つからないためlstatで拒否する。
    try { if (lstatSync(current).isSymbolicLink()) throw new Error(`Symlink target refused: ${current}`); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
}
function propertyValue(value) {
  return value.split('').map((char) => {
    const code = char.charCodeAt(0);
    if (code < 32 || code > 126) return `\\u${code.toString(16).padStart(4, '0')}`;
    if ('\\:=#!'.includes(char) || char === ' ') return `\\${char}`;
    return char;
  }).join('');
}
function initialize() {
  const args = process.argv.slice(2); const values = {}; let dryRun = false;
  while (args.length) {
    const option = args.shift();
    if (option === '--dry-run' && !dryRun) { dryRun = true; continue; }
    const key = option?.replace(/^--/, '');
    if (!option?.startsWith('--') || !fields.includes(key) || key in values || !args.length) throw new Error(`Unknown, duplicate or incomplete option: ${option}`);
    values[key] = args.shift();
  }
  if (!fields.every((key) => typeof values[key] === 'string' && values[key].trim() && !/[\x00-\x1f\x7f]/.test(values[key]))) throw new Error('All six initialization values are required without control characters');
  if (!/^[a-z][a-z0-9_]{1,63}$/.test(values['mod-id']) || ['minecraft', 'neoforge', 'forge', 'kotlinforforge'].includes(values['mod-id'])) throw new Error('Invalid MOD ID');
  for (const key of ['package', 'group']) {
    if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/.test(values[key]) || values[key].split('.').some((part) => reserved.has(part))) throw new Error(`Invalid ${key}`);
  }
  const url = new URL(values['repo-url']);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error('Repository URL must be HTTPS without credentials, query or fragment');
  const recordPath = join(root, '.template-initialized.json'); safePath(recordPath);
  if (existsSync(recordPath)) throw new Error('Template is already initialized');
  safePath(join(root, 'template-version.txt'));
  const version = readFileSync(join(root, 'template-version.txt'), 'utf8').trim();
  const changes = [];
  const replacements = new Map([[originalPackage, values.package], [originalId, values['mod-id']], ['https://github.com/moco-server/minecraft-mod-template', values['repo-url']]]);
  // 元のplaceholderだけを一度走査し、入力内のplaceholderや$記号を再解釈しない。
  const replace = (text) => text.replace(/org\.mocoserver\.modtemplate|mod_template|https:\/\/github\.com\/moco-server\/minecraft-mod-template/g, (match) => replacements.get(match));
  function add(path, destination = path, transform = replace) {
    safePath(path); safePath(destination);
    if (!existsSync(path)) return;
    if (path !== destination && existsSync(destination)) throw new Error(`Destination already exists: ${destination}`);
    const content = readFileSync(path);
    const output = /\.nbt$/.test(path) ? content : Buffer.from(transform(content.toString('utf8')));
    if (path !== destination || !content.equals(output)) changes.push({ path, destination, output });
  }
  const properties = join(root, 'gradle.properties');
  if (!existsSync(properties)) throw new Error('Missing gradle.properties');
  add(properties, properties, (text) => {
    const updates = { mod_id: values['mod-id'], mod_name: values.name, mod_package: values.package, mod_group_id: values.group, mod_authors: values.author, mod_repo_url: values['repo-url'], mod_description: `${values.name} MOD` };
    for (const [key, value] of Object.entries(updates)) {
      const pattern = new RegExp(`^${key}=.*$`, 'm');
      if (!pattern.test(text)) throw new Error(`Missing Gradle property: ${key}`);
      text = text.replace(pattern, () => `${key}=${propertyValue(value)}`);
    }
    return text;
  });
  add(join(root, 'settings.gradle.kts'), join(root, 'settings.gradle.kts'), (text) => text.replace('rootProject.name = \"minecraft-mod-template\"', `rootProject.name = \"${values['mod-id']}\"`));
  add(join(root, 'package.json'), join(root, 'package.json'), (text) => { const manifest = JSON.parse(text); manifest.name = `${values['mod-id']}-tools`; return JSON.stringify(manifest, null, 2) + '\n'; });
  for (const path of ['README.md', 'AGENTS.md', 'docs/design.md', '.agents/skills/mod-implement/references/development.md', 'docs/verification.md', 'docs/compatibility.md', '.agents/skills/mod-kotlin/references/coding-conventions.md', 'docs/template-updates.md', 'docs/external-skills.md', 'docs/client-acceptance.md']) add(join(root, path));
  const oldPath = originalPackage.replaceAll('.', sep); const newPath = values.package.replaceAll('.', sep);
  const roots = ['core/src/main/kotlin', 'core/src/test/kotlin', 'neoforge/src/main/kotlin', 'neoforge/src/gametest/kotlin', 'neoforge/src/main/resources', 'neoforge/src/main/templates', 'neoforge/src/gametest/resources', 'neoforge/src/gametest/templates', 'neoforge/src/generated/resources'];
  function walk(path) {
    safePath(path);
    if (!existsSync(path)) return;
    for (const name of readdirSync(path)) {
      if (name === '.cache') continue;
      const file = join(path, name); safePath(file);
      if (lstatSync(file).isDirectory()) walk(file);
      else if (/\.(kt|json|toml|nbt)$/.test(file)) {
        const destination = file.replace(`${sep}${oldPath}${sep}`, `${sep}${newPath}${sep}`).replace(`${sep}assets${sep}${originalId}${sep}`, `${sep}assets${sep}${values['mod-id']}${sep}`).replace(`${sep}data${sep}${originalId}${sep}`, `${sep}data${sep}${values['mod-id']}${sep}`);
        add(file, destination);
      }
    }
  }
  roots.forEach((path) => walk(join(root, path)));
  const destinations = changes.map((change) => change.destination);
  if (new Set(destinations).size !== destinations.length) throw new Error('Conflicting initialization destinations');
  for (const change of changes) console.log(`${relative(root, change.path)} -> ${relative(root, change.destination)}`);
  if (dryRun) { console.log('Dry run: no changes written'); return; }
  for (const change of changes) {
    mkdirSync(dirname(change.destination), { recursive: true });
    writeFileSync(change.destination, change.output);
  }
  for (const change of changes.filter(({ path, destination }) => path !== destination)) {
    unlinkSync(change.path);
    let directory = dirname(change.path);
    while (directory !== root && readdirSync(directory).length === 0) { rmdirSync(directory); directory = dirname(directory); }
  }
  writeFileSync(recordPath, JSON.stringify({ templateVersion: version, modId: values['mod-id'], package: values.package, group: values.group, name: values.name, author: values.author, repoUrl: values['repo-url'] }, null, 2) + '\n');
  console.log('Initialized. Run datagen, then check/build/GameTest in a separate Gradle invocation.');
}
try { initialize(); } catch (error) { console.error(error.message); process.exitCode = 1; }
