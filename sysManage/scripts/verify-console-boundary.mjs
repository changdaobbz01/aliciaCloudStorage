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
const appSource = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
assert.match(appSource, /path="\/:view"/, 'cloud console must expose URL-addressable child routes');
assert.match(appSource, /<Navigate to="\/users" replace \/>/, 'cloud console root and unknown routes must land on users');
assert.match(appSource, /new URLSearchParams\(search\)\.get\('view'\)/, 'cloud console root route must read legacy view query');
assert.match(appSource, /'appPackage'[\s\S]*'\/app-package'/, 'cloud console root route must support the legacy appPackage query');
assert.match(
  appSource,
  /defaultCloudViewRoute\(location\.search\)[\s\S]*location\.search[\s\S]*location\.hash/,
  'cloud console root route must preserve query and hash',
);
assert.match(consolePageSource, /useParams<\{ view\?: string \}>/, 'cloud console must read the active view from the URL');
assert.match(consolePageSource, /routeByView/, 'cloud console menu must map internal view keys to stable URL routes');
assert.match(consolePageSource, /appPackage:[\s\S]*'\/app-package'/, 'cloud console APK package view must use the app-package URL route');
assert.ok(
  consolePageSource.includes('document.title = `${activeMeta.title} - Alicia 云盘后台`;'),
  'cloud console document title must follow the active view',
);
assert.match(consolePageSource, /key:\s*'consoleHome'/, 'cloud console account menu must expose the unified console gateway');
assert.match(
  consolePageSource,
  /window\.location\.assign\('\/console\/'\)/,
  'cloud console account menu must route management navigation through /console/',
);
assert.match(
  consolePageSource,
  /title="没有云盘后台权限"[\s\S]*href="\/console\/"[\s\S]*href="\/cloudPan\/"[\s\S]*href="\/"/,
  'cloud console permission denied state must expose routes back to the console gateway, cloud web, and main site',
);

console.log('[OK] cloud console boundary verified');
