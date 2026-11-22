import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readModVersion, releaseNotes, syncModVersion } from '../release-version.mjs';

test('syncs the Changesets version into exactly one Gradle property', () => {
  const properties = 'mod_id=example\r\nmod_version=0.1.0-dev\r\nother_version=1\r\n';
  const updated = syncModVersion(properties, '0.1.0');
  assert.equal(updated, 'mod_id=example\r\nmod_version=0.1.0\r\nother_version=1\r\n');
  assert.equal(readModVersion(updated), '0.1.0');
  assert.throws(() => syncModVersion(properties.repeat(2), '0.1.0'), /exactly one/);
});

test('extracts the requested Changesets release notes and keeps subsection headings', () => {
  const changelog = '# Example\r\n\r\n## 0.2.0\r\n\r\n### Minor Changes\r\n\r\n- New feature\r\n\r\n## 0.1.0\r\n\r\n- First release\r\n';
  assert.equal(releaseNotes(changelog, '0.2.0'), '### Minor Changes\n\n- New feature\n');
  assert.equal(releaseNotes(changelog, '0.1.0'), '- First release\n');
  assert.throws(() => releaseNotes(changelog, '0.3.0'), /No release notes/);
});
