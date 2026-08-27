import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { extname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const srcRoot = new URL('../src/', import.meta.url);
const srcRootPath = fileURLToPath(srcRoot);
const sourceExtensions = new Set(['.ts', '.tsx']);
const forbiddenPatterns = [
  { pattern: /\/api\/identity\/admin\//, reason: 'identity administration belongs in mainSite/userSite' },
  { pattern: /\/api\/admin\/users\b/, reason: 'legacy identity admin routes must not return to cloud console' },
  { pattern: /\/api\/storage\//, reason: 'personal file operations belong in webApp' },
  { pattern: /\/api\/share-links\//, reason: 'personal share operations belong in webApp' },
  { pattern: /\/api\/public\/share-links\//, reason: 'public share pages belong in webApp' },
  { pattern: /\b(IdentityAudit|IdentityApplicationRole|UpdateIdentityApplicationRole)[A-Za-z0-9_]*\b/, reason: 'identity admin types belong in mainSite/userSite' },
  { pattern: /\b(StorageViewMode|StorageFileCategory|StorageNodeFilter|StorageNodeSortField)\b/, reason: 'personal drive types belong in webApp' },
  { pattern: /\b(fetchIdentitySessions|revokeIdentitySession|changePassword|updateProfile)\b/, reason: 'personal identity settings belong in webApp or userSite' },
  { pattern: /\b(fetchDriveOverview|fetchStorageNodes|createFolder|uploadStorageFile|createShareLink)\b/, reason: 'personal drive API calls belong in webApp' },
  { pattern: /\b(downloadStorage|renameStorage|moveStorage|deleteStorage|restoreStorage|permanentlyDelete)\b/, reason: 'personal drive mutation API calls belong in webApp' },
];

function listSourceFiles(directoryUrl) {
  return readdirSync(directoryUrl, { withFileTypes: true }).flatMap((entry) => {
    const entryUrl = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, directoryUrl);

    if (entry.isDirectory()) {
      return listSourceFiles(entryUrl);
    }

    return sourceExtensions.has(extname(entry.name)) ? [entryUrl] : [];
  });
}

for (const fileUrl of listSourceFiles(srcRoot)) {
  const relativePath = relative(srcRootPath, fileURLToPath(fileUrl)).replaceAll('\\', '/');
  const source = readFileSync(fileUrl, 'utf8');

  for (const { pattern, reason } of forbiddenPatterns) {
    assert.doesNotMatch(source, pattern, `${relativePath} must not contain ${pattern}: ${reason}`);
  }
}

const consolePageSource = readFileSync(new URL('../src/pages/CloudConsolePage.tsx', import.meta.url), 'utf8');
assert.match(consolePageSource, /key:\s*'consoleHome'/, 'cloud console account menu must expose the unified console gateway');
assert.match(
  consolePageSource,
  /window\.location\.assign\('\/console\/'\)/,
  'cloud console account menu must route management navigation through /console/',
);

console.log('[OK] cloud console boundary verified');
