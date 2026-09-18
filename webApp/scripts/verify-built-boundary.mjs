import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const distRoot = fileURLToPath(new URL('../dist/', import.meta.url));
const textArtifactExtensions = new Set(['.css', '.html', '.js', '.json', '.txt']);
const forbiddenMarkers = [
  { marker: '/api/admin/', reason: 'cloud administration APIs belong in sysManage' },
  { marker: '/api/identity/admin/', reason: 'identity administration APIs belong in mainSite/userSite' },
  { marker: '/console/cloud', reason: 'the cloud console route must not be shipped in the cloud user client' },
  { marker: '/console/identity', reason: 'the identity console route must not be shipped in the cloud user client' },
  { marker: 'CLOUD_ADMIN', reason: 'cloud administrator role checks belong in sysManage' },
  { marker: 'sysManage', reason: 'cloud console implementation markers must not be shipped in the cloud user client' },
];

function listTextArtifacts(directoryPath) {
  return readdirSync(directoryPath, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = join(directoryPath, entry.name);

    if (entry.isDirectory()) {
      return listTextArtifacts(entryPath);
    }

    return textArtifactExtensions.has(extname(entry.name)) ? [entryPath] : [];
  });
}

const artifacts = listTextArtifacts(distRoot);
assert.ok(artifacts.length > 0, 'cloud web build output must contain text artifacts');

const violations = artifacts.flatMap((artifactPath) => {
  const source = readFileSync(artifactPath, 'utf8');
  const artifactName = relative(distRoot, artifactPath).replaceAll('\\', '/');

  return forbiddenMarkers
    .filter(({ marker }) => source.includes(marker))
    .map(({ marker, reason }) => ({ artifactName, marker, reason }));
});

assert.deepEqual(
  violations,
  [],
  `cloud web build output contains admin frontend markers:\n${violations
    .map(({ artifactName, marker, reason }) => `  ${artifactName}: ${marker} (${reason})`)
    .join('\n')}`,
);

console.log(`[OK] cloud web built client boundary verified (${artifacts.length} artifacts)`);
