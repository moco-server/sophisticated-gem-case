import { readdirSync, mkdirSync, cpSync, lstatSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function collectModJar(root) {
  const library = join(root, 'neoforge/build/libs');
  const candidates = readdirSync(library).filter((name) => name.endsWith('.jar') && !/-(sources|javadoc|gametests)\.jar$/.test(name));
  if (candidates.length !== 1) throw new Error(`Expected one normal MOD JAR, found ${candidates.length}: ${candidates.join(', ')}`);
  const source = join(library,candidates[0]);
  if (!lstatSync(source).isFile() || lstatSync(source).isSymbolicLink()) throw new Error('Normal JAR must be a regular file');
  const output = join(root,'.local/artifact');
  mkdirSync(output,{recursive:true});
  if (readdirSync(output).length !== 0) throw new Error('Artifact output must be empty');
  cpSync(source,join(output,candidates[0]));
  return join(output,candidates[0]);
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(collectModJar(fileURLToPath(new URL('../',import.meta.url)))); }
  catch (error) { console.error(error.message); process.exitCode=1; }
}
