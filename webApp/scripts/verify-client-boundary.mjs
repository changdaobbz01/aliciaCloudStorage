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

function extractForeignRoutePathLiterals(fileUrl) {
  const source = readFileSync(fileUrl, 'utf8');
  const routePathPattern = /(['"`])(\/(?:console|rag)(?:[^'"`]*)?)\1/g;
  const paths = [];
  let match;

  while ((match = routePathPattern.exec(source)) !== null) {
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
    .filter(({ path, relativePath }) => !isReturnPathBoundarySentinel(relativePath, path))
    .filter(({ path }) => !allowedPrefixes.some((allowedPrefix) => matchesOwnedApiPrefix(path, allowedPrefix)));

  assert.deepEqual(
    violations,
    [],
    `${label} contains API paths outside its frontend/API ownership:\n${violations
      .map(({ relativePath, line, path }) => `  ${relativePath}:${line} ${path}`)
      .join('\n')}\nAllowed prefixes: ${allowedPrefixes.join(', ')}`,
  );
}

function isReturnPathBoundarySentinel(relativePath, path) {
  return (
    relativePath === 'lib/unifiedLogin.ts' &&
    ['/api', '/api/', '/console', '/console/', '/rag', '/rag/'].includes(path)
  );
}

function assertNoForeignRoutePathLiterals(fileUrls, label) {
  const violations = fileUrls
    .flatMap((fileUrl) => extractForeignRoutePathLiterals(fileUrl))
    .filter(({ path, relativePath }) => !isReturnPathBoundarySentinel(relativePath, path));

  assert.deepEqual(
    violations,
    [],
    `${label} must not expose foreign route paths from the cloud web user client:\n${violations
      .map(({ relativePath, line, path }) => `  ${relativePath}:${line} ${path}`)
      .join('\n')}`,
  );
}

const files = listSourceFiles(srcRoot);
const cloudWebApiScopeFiles = files;
const cloudWebAllowedApiPrefixes = [
  '/api/health',
  '/api/cloud-profile/',
  '/api/identity/auth/',
  '/api/storage/',
  '/api/share-links',
  '/api/public/share-links',
  '/api/app-package',
];

for (const fileUrl of files) {
  const relativePath = relative(srcRootPath, fileURLToPath(fileUrl)).replaceAll('\\', '/');
  const source = readFileSync(fileUrl, 'utf8');

  for (const { pattern, reason } of forbiddenPatterns) {
    assert.doesNotMatch(source, pattern, `${relativePath} must not contain ${pattern}: ${reason}`);
  }
}

assertApiPathsMatchAllowedPrefixes(
  cloudWebApiScopeFiles,
  cloudWebAllowedApiPrefixes,
  'cloud web source',
);
assertNoForeignRoutePathLiterals(files, 'cloud web source');

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
const driveSharesView = readFileSync(new URL('../src/features/drive/DriveSharesView.tsx', import.meta.url), 'utf8');
const useDriveShares = readFileSync(new URL('../src/features/drive/hooks/useDriveShares.ts', import.meta.url), 'utf8');
const driveShared = readFileSync(new URL('../src/features/drive/driveShared.ts', import.meta.url), 'utf8');
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
assert.doesNotMatch(
  drivePage,
  /账号管理|用户管理|云盘后台|身份后台/,
  'cloud drive page must describe user-facing profile tools without admin management wording',
);
assert.doesNotMatch(
  drivePage,
  /APK|等待上传|开放下载/,
  'cloud web app download panel must use Android installer copy without APK upload/admin wording',
);
assert.doesNotMatch(
  appDownloadPage,
  /发布状态|暂未发布|未发布/,
  'cloud app download page must describe package availability as a user-facing download state',
);
assert.match(
  driveShared,
  /function normalizeAppDownloadPath\(downloadPath = APP_DOWNLOAD_PUBLIC_PATH\)/,
  'cloud web app download URL resolver must normalize download paths',
);
assert.match(
  driveShared,
  /url\.origin !== currentOrigin \|\| url\.pathname !== APP_DOWNLOAD_PUBLIC_PATH/,
  'cloud web app download URL resolver must stay on the public package endpoint',
);
assert.match(
  driveShared,
  /return new URL\(safeDownloadPath, window\.location\.origin\)\.toString\(\);/,
  'cloud web app download URL resolver must produce same-origin public download URLs',
);
assert.match(
  useDriveShares,
  /const \[shareRevokingId, setShareRevokingId\] = useState<number \| null>\(null\);[\s\S]*shareRevokingIdRef = useRef<number \| null>\(null\);/,
  'cloud web share revocation must track the pending share id',
);
assert.match(
  useDriveShares,
  /if \(shareRevokingIdRef\.current !== null\) \{[\s\S]*?return;[\s\S]*?\}[\s\S]*shareRevokingIdRef\.current = shareId;[\s\S]*setShareRevokingId\(shareId\);[\s\S]*finally \{[\s\S]*shareRevokingIdRef\.current = null;[\s\S]*setShareRevokingId\(null\);/,
  'cloud web share revocation must block duplicate submissions and clear pending state',
);
assert.match(
  driveSharesView,
  /okButtonProps=\{\{ danger: true, loading: shareRevoking, disabled: shareRevokingId !== null && !shareRevoking \}\}[\s\S]*cancelButtonProps=\{\{ disabled: shareRevoking \}\}[\s\S]*loading=\{shareRevoking\}[\s\S]*disabled=\{shareRevokingId !== null && !shareRevoking\}/,
  'cloud web shares view must surface pending share revocation',
);
assert.match(
  driveSharesView,
  /<Button icon=\{<Icon icon=\{RefreshCw\} \/>\} disabled=\{shareRevokingId !== null\} onClick=\{onRefresh\}>/,
  'cloud web shares view must not refresh while a share revocation is pending',
);
assert.match(
  drivePage,
  /shareRevokingId=\{shares\.shareRevokingId\}/,
  'cloud drive page must wire share revocation pending state to the UI',
);
assert.match(
  driveProfileModals,
  /title=\{<AliciaModalTitle eyebrow="Account">个人资料<\/AliciaModalTitle>\}[\s\S]*rootClassName="alicia-modal alicia-account-modal account-profile-modal"[\s\S]*className="account-profile-form"[\s\S]*className="profile-avatar-row account-profile-hero"[\s\S]*<strong>用户图标<\/strong>[\s\S]*上传本地图片或填写图片地址后，会同步更新所有 Alicia 账号入口。[\s\S]*className="account-profile-actions"[\s\S]*name="nickname"[\s\S]*name="phoneNumber"[\s\S]*name="avatarUrl"/,
  'cloud web profile modal must keep the shared profile dialog contract',
);
assert.doesNotMatch(
  driveProfileModals,
  /profile-avatar-preview-row|profile-avatar-actions/,
  'cloud web profile modal must not keep legacy profile layout aliases',
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
assert.match(clientStyles, /\.account-profile-form/, 'cloud web styles must define the unified account profile form');
assert.match(clientStyles, /\.account-profile-hero/, 'cloud web styles must define the unified profile avatar function area');
assert.match(clientStyles, /\.account-profile-copy/, 'cloud web styles must define the unified profile explanatory copy');
assert.match(clientStyles, /\.account-profile-actions/, 'cloud web styles must define the unified profile action row');
assert.match(clientStyles, /\.account-profile-fields/, 'cloud web styles must define the unified profile field stack');
assert.doesNotMatch(
  clientStyles,
  /\.profile-avatar-preview-row\b|\.profile-avatar-actions\b/,
  'cloud web styles must not keep legacy profile layout aliases',
);

console.log('[OK] cloud web client boundary verified');
