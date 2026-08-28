import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { extname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const srcRoot = new URL('../src/', import.meta.url);
const srcRootPath = fileURLToPath(srcRoot);
const sourceExtensions = new Set(['.ts', '.tsx']);
const forbiddenPatterns = [
  { pattern: /\/api\/admin\//, reason: 'admin API calls belong in sysManage' },
  { pattern: /\/api\/identity\/admin\//, reason: 'identity admin API calls belong in mainSite/userSite' },
  { pattern: /\b(isCloudAdmin|CLOUD_ADMIN)\b/, reason: 'cloud admin role checks belong in sysManage' },
  { pattern: /\bAdminCloud[A-Za-z0-9_]*\b/, reason: 'admin cloud types belong in sysManage' },
  { pattern: /\bIdentityAudit[A-Za-z0-9_]*\b/, reason: 'identity audit types belong in mainSite/userSite' },
  { pattern: /\b(fetchUsers|createUser|updateUserStorageQuota|resetUserPassword)\b/, reason: 'user administration belongs outside webApp' },
  { pattern: /\b(fetchAdminCloud|fetchAdminAppPackage|uploadAdminAppPackage|deleteAdminAppPackage)\b/, reason: 'cloud administration belongs in sysManage' },
];
const forbiddenStyleClassPatterns = [
  { pattern: /\.account-admin-tabs\b/, reason: 'account administration styles belong outside webApp' },
  { pattern: /\.audit-(filter|quick|result)/, reason: 'identity audit styles belong in mainSite/userSite' },
  { pattern: /\.operations-/, reason: 'cloud operations styles belong in sysManage' },
  { pattern: /\.app-package-(summary|grid|card|link|url|meta|release-notes|list)/, reason: 'APK administration styles belong in sysManage' },
  { pattern: /\.management-summary-/, reason: 'operations summary styles belong in sysManage' },
  { pattern: /\.user-cell-copy\b|\.user-chip\b|\.table-secondary-text\b/, reason: 'admin table cell styles belong outside webApp' },
];
const forbiddenFileNames = [
  /DriveAccounts/,
  /DriveOperations/,
  /DriveAppPackage/,
  /IdentityAuditLogPanel/,
  /UserManagementPanel/,
  /useDrive.*Admin/,
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

function extractApiPathLiterals(fileUrl) {
  const source = readFileSync(fileUrl, 'utf8');
  const apiPathPattern = /(['"`])(\/api\/[\s\S]*?)\1/g;
  const paths = [];
  let match;

  while ((match = apiPathPattern.exec(source)) !== null) {
    paths.push({
      path: match[2],
      relativePath: relative(srcRootPath, fileURLToPath(fileUrl)).replaceAll('\\', '/'),
      line: source.slice(0, match.index).split('\n').length,
    });
  }

  return paths;
}

function matchesOwnedApiPrefix(path, allowedPrefix) {
  if (allowedPrefix.endsWith('/')) {
    return path.startsWith(allowedPrefix);
  }

  return (
    path === allowedPrefix ||
    path.startsWith(`${allowedPrefix}/`) ||
    path.startsWith(`${allowedPrefix}?`) ||
    path.startsWith(`${allowedPrefix}\${`)
  );
}

function assertApiPathsMatchAllowedPrefixes(fileUrls, allowedPrefixes, label) {
  const violations = fileUrls
    .flatMap((fileUrl) => extractApiPathLiterals(fileUrl))
    .filter(({ path }) => !allowedPrefixes.some((allowedPrefix) => matchesOwnedApiPrefix(path, allowedPrefix)));

  assert.deepEqual(
    violations,
    [],
    `${label} contains API paths outside its frontend/API ownership:\n${violations
      .map(({ relativePath, line, path }) => `  ${relativePath}:${line} ${path}`)
      .join('\n')}\nAllowed prefixes: ${allowedPrefixes.join(', ')}`,
  );
}

const files = listSourceFiles(srcRoot);

for (const fileUrl of files) {
  const relativePath = relative(srcRootPath, fileURLToPath(fileUrl)).replaceAll('\\', '/');
  const source = readFileSync(fileUrl, 'utf8');

  for (const { pattern, reason } of forbiddenPatterns) {
    assert.doesNotMatch(source, pattern, `${relativePath} must not contain ${pattern}: ${reason}`);
  }
}

assertApiPathsMatchAllowedPrefixes(
  [new URL('../src/lib/api.ts', import.meta.url), new URL('../src/features/drive/driveShared.ts', import.meta.url)],
  [
    '/api/health',
    '/api/cloud-profile/',
    '/api/identity/auth/',
    '/api/storage/',
    '/api/share-links',
    '/api/public/share-links',
    '/api/app-package',
  ],
  'cloud web',
);

for (const entry of readdirSync(srcRoot, { recursive: true })) {
  const relativePath = String(entry).replaceAll('\\', '/');
  const entryPath = join(srcRootPath, relativePath);

  if (!statSync(entryPath).isFile()) {
    continue;
  }

  for (const pattern of forbiddenFileNames) {
    assert.doesNotMatch(relativePath, pattern, `cloud web must not keep admin file ${relativePath}`);
  }
}

const types = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8');
assert.doesNotMatch(types, /'accounts'|'operations'|'appPackage'/, 'StorageViewMode must stay user-facing only');

const rootApp = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
assert.doesNotMatch(rootApp, /path="\/console/, 'cloud web must not mount console routes');

const drivePage = readFileSync(new URL('../src/pages/DrivePage.tsx', import.meta.url), 'utf8');
const driveProfileModals = readFileSync(new URL('../src/features/drive/DriveProfileModals.tsx', import.meta.url), 'utf8');
const appDownloadPage = readFileSync(new URL('../src/pages/AppDownloadPage.tsx', import.meta.url), 'utf8');
const sharePage = readFileSync(new URL('../src/pages/SharePage.tsx', import.meta.url), 'utf8');
const unifiedLoginRedirectPage = readFileSync(new URL('../src/pages/UnifiedLoginRedirectPage.tsx', import.meta.url), 'utf8');
const mobileApp = readFileSync(new URL('../src/lib/mobileApp.ts', import.meta.url), 'utf8');
const assetLinks = readFileSync(new URL('../public/.well-known/assetlinks.json', import.meta.url), 'utf8');
const assetLinkPackages = JSON.parse(assetLinks).map((entry) => entry.target?.package_name);
assert.doesNotMatch(
  drivePage,
  /管理控制台|consoleHome|\/console(?:\/|\b)/,
  'cloud web account menu must not expose admin console entry points',
);
assert.match(driveProfileModals, /className="account-profile-form"/, 'cloud web profile modal must use the unified account profile form layout');
assert.match(driveProfileModals, /name="avatarUrl"/, 'cloud web profile modal must keep the shared avatar URL field');
assert.match(driveProfileModals, /className="profile-avatar-row account-profile-hero"/, 'cloud web profile modal must keep the shared avatar function area');
assert.match(drivePage, /document\.title = `\$\{currentViewLabel\} - Alicia/, 'cloud drive page document title must follow the active view');
assert.match(appDownloadPage, /document\.title = '.+Alicia/, 'cloud app download page document title must be explicit');
assert.match(sharePage, /document\.title = shareTitle \?/, 'cloud share page document title must follow the loaded share title');
assert.match(unifiedLoginRedirectPage, /document\.title = '.+Alicia/, 'cloud login redirect page document title must be explicit');
assert.match(
  mobileApp,
  /DEFAULT_ANDROID_PACKAGE_NAME = 'com\.alicia\.cloudstorage\.phone'/,
  'cloud web intent fallback package must match the official Android applicationId',
);
assert.deepEqual(assetLinkPackages, ['com.alicia.cloudstorage.phone'], 'cloud asset links must authorize only the official Android package');

const clientStyles = readFileSync(new URL('../src/index.css', import.meta.url), 'utf8');
for (const { pattern, reason } of forbiddenStyleClassPatterns) {
  assert.doesNotMatch(clientStyles, pattern, `webApp styles must not contain ${pattern}: ${reason}`);
}

console.log('[OK] cloud web client boundary verified');
