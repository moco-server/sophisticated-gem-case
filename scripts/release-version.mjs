import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const modVersionProperty = /^mod_version=([^\s]+)\r?$/gm;
const stableVersion = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

export function readModVersion(properties) {
  const matches = [...properties.matchAll(modVersionProperty)];
  if (matches.length !== 1) throw new Error('Expected exactly one mod_version property');
  return matches[0][1];
}

export function syncModVersion(properties, version) {
  readModVersion(properties);
  return properties.replace(modVersionProperty, (line) => `mod_version=${version}${line.endsWith('\r') ? '\r' : ''}`);
}

export function releaseNotes(changelog, version) {
  const sections = changelog.replaceAll('\r\n', '\n').split(/^## /m).slice(1);
  const section = sections.find((entry) => entry.split('\n', 1)[0] === version);
  const notes = section?.slice(version.length).trim();
  if (!notes) throw new Error(`No release notes for ${version}`);
  return `${notes}\n`;
}

function main([command, previousVersion]) {
  const version = JSON.parse(readFileSync('package.json', 'utf8')).version;
  const properties = readFileSync('gradle.properties', 'utf8');

  if (command === 'sync') {
    writeFileSync('gradle.properties', syncModVersion(properties, version));
    return;
  }

  if (command !== 'plan' && command !== 'notes') throw new Error(`Invalid command: ${command}`);
  if (readModVersion(properties) !== version) throw new Error('Changesets and Gradle versions differ');

  if (command === 'plan' && (version === previousVersion || !stableVersion.test(version))) return;
  if (!stableVersion.test(version)) throw new Error(`Expected a stable release version: ${version}`);

  const notes = releaseNotes(readFileSync('CHANGELOG.md', 'utf8'), version);
  if (command === 'notes') {
    process.stdout.write(notes);
  } else {
    appendFileSync(process.env.GITHUB_OUTPUT, `version=${version}\ntag=v${version}\n`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2));
}
