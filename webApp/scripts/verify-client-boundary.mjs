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

function countMatches(source, pattern) {
  return [...source.matchAll(pattern)].length;
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
const driveExplorerView = readFileSync(new URL('../src/features/drive/DriveExplorerView.tsx', import.meta.url), 'utf8');
const driveStorageActionModals = readFileSync(new URL('../src/features/drive/DriveStorageActionModals.tsx', import.meta.url), 'utf8');
const driveShareCreateModal = readFileSync(new URL('../src/features/drive/DriveShareCreateModal.tsx', import.meta.url), 'utf8');
const driveSharesView = readFileSync(new URL('../src/features/drive/DriveSharesView.tsx', import.meta.url), 'utf8');
const driveDownloadsView = readFileSync(new URL('../src/features/drive/DriveDownloadsView.tsx', import.meta.url), 'utf8');
const storageTable = readFileSync(new URL('../src/components/StorageTable.tsx', import.meta.url), 'utf8');
const driveTypes = readFileSync(new URL('../src/features/drive/types.ts', import.meta.url), 'utf8');
const useDriveDownloads = readFileSync(new URL('../src/features/drive/hooks/useDriveDownloads.ts', import.meta.url), 'utf8');
const useDriveExplorer = readFileSync(new URL('../src/features/drive/hooks/useDriveExplorer.ts', import.meta.url), 'utf8');
const useDriveShares = readFileSync(new URL('../src/features/drive/hooks/useDriveShares.ts', import.meta.url), 'utf8');
const useDriveStorageDialogs = readFileSync(new URL('../src/features/drive/hooks/useDriveStorageDialogs.ts', import.meta.url), 'utf8');
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
  appDownloadPage,
  /import \{ useEffect, useMemo, useRef, useState \} from 'react';[\s\S]*const packageLoadingRef = useRef\(false\);[\s\S]*const downloadOpeningRef = useRef\(false\);[\s\S]*const shareOpeningRef = useRef\(false\);/,
  'cloud app download page must track synchronous pending guards',
);
assert.match(
  appDownloadPage,
  /async function loadPackage\(shouldIgnoreResult: \(\) => boolean = \(\) => false\) \{[\s\S]*if \(packageLoadingRef\.current\) \{[\s\S]*return;[\s\S]*packageLoadingRef\.current = true;[\s\S]*setLoading\(true\);[\s\S]*const nextPackageInfo = await fetchPublicAppPackage\(\);[\s\S]*finally \{[\s\S]*packageLoadingRef\.current = false;[\s\S]*setLoading\(false\);/,
  'cloud app download package load must block duplicate reads and expose retry state',
);
assert.match(
  appDownloadPage,
  /function openShareInApp\(\) \{[\s\S]*if \(!shareCode \|\| shareOpeningRef\.current \|\| downloadOpeningRef\.current\) \{[\s\S]*shareOpeningRef\.current = true;[\s\S]*setShareOpening\(true\);[\s\S]*window\.location\.assign\(buildShareIntentUrl\(shareCode\)\);[\s\S]*function handleDownload\(\) \{[\s\S]*if \(downloadOpeningRef\.current \|\| shareOpeningRef\.current\) \{[\s\S]*downloadOpeningRef\.current = true;[\s\S]*setDownloadOpening\(true\);[\s\S]*window\.location\.assign\(downloadUrl\);/,
  'cloud app download actions must block duplicate navigation',
);
assert.match(
  appDownloadPage,
  /extra=\{[\s\S]*loading=\{loading\}[\s\S]*disabled=\{loading\}[\s\S]*loading=\{shareOpening\}[\s\S]*disabled=\{downloadOpening\}[\s\S]*loading=\{downloadOpening\}[\s\S]*disabled=\{!canDownload \|\| shareOpening\}[\s\S]*disabled=\{appDownloadActionPending\}/,
  'cloud app download controls must surface pending loading and navigation state',
);
assert.match(
  drivePage,
  /import \{ Suspense, lazy, useEffect, useMemo, useRef, useState \} from 'react';[\s\S]*const publicAppPackageLoadingRef = useRef\(false\);[\s\S]*async function loadPublicAppPackageInfo\(shouldIgnoreResult: \(\) => boolean = \(\) => false\) \{[\s\S]*if \(publicAppPackageLoadingRef\.current\) \{[\s\S]*return;[\s\S]*publicAppPackageLoadingRef\.current = true;[\s\S]*setPublicAppPackageLoading\(true\);[\s\S]*const nextPackageInfo = await fetchPublicAppPackage\(\);[\s\S]*setPublicAppPackageInfo\(nextPackageInfo\);[\s\S]*finally \{[\s\S]*publicAppPackageLoadingRef\.current = false;[\s\S]*setPublicAppPackageLoading\(false\);/,
  'cloud drive app package reads must keep synchronous loading guards',
);
assert.match(
  drivePage,
  /void loadPublicAppPackageInfo\(\(\) => cancelled\);[\s\S]*refreshPending=\{publicAppPackageLoading\}/,
  'cloud drive app package loading must be wired to list refresh',
);
assert.match(
  driveExplorerView,
  /refreshPending: boolean;[\s\S]*refreshPending,[\s\S]*loading=\{refreshPending\}[\s\S]*disabled=\{storageMutationPending \|\| refreshPending\}/,
  'cloud web explorer refresh must surface app package loading state',
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
  /type ShareLinksLoadOptions = \{[\s\S]*force\?: boolean;[\s\S]*const authTokenRef = useRef\(authToken\);[\s\S]*const shareLinksRequestIdRef = useRef\(0\);[\s\S]*const shareLinksLoadingKeyRef = useRef<string \| null>\(null\);[\s\S]*authTokenRef\.current = authToken;/,
  'cloud web share list reads must track request identity',
);
assert.match(
  useDriveShares,
  /function createShareLinksRequestKey\(token: string \| null = authToken\) \{[\s\S]*return JSON\.stringify\(\[token\]\);[\s\S]*function isCurrentShareLinksRequest\(requestId: number, requestKey: string\) \{[\s\S]*shareLinksRequestIdRef\.current === requestId[\s\S]*shareLinksLoadingKeyRef\.current === requestKey[\s\S]*createShareLinksRequestKey\(authTokenRef\.current\) === requestKey/,
  'cloud web share list reads must compare the visible auth scope',
);
assert.match(
  useDriveShares,
  /useEffect\(\(\) => \{[\s\S]*shareLinksRequestIdRef\.current \+= 1;[\s\S]*shareLinksLoadingKeyRef\.current = null;[\s\S]*setShareLinksLoading\(false\);[\s\S]*if \(!authToken\) \{[\s\S]*setShareLinks\(\[\]\);[\s\S]*\}, \[authToken\]\);/,
  'cloud web share list reads must invalidate when auth scope changes',
);
assert.match(
  useDriveShares,
  /async function loadShareLinks\(options: ShareLinksLoadOptions = \{\}\) \{[\s\S]*if \(!authToken\) \{[\s\S]*shareLinksRequestIdRef\.current \+= 1;[\s\S]*shareLinksLoadingKeyRef\.current = null;[\s\S]*const requestKey = createShareLinksRequestKey\(\);[\s\S]*if \(!options\.force && shareLinksLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*shareLinksRequestIdRef\.current \+= 1;[\s\S]*const requestId = shareLinksRequestIdRef\.current;[\s\S]*shareLinksLoadingKeyRef\.current = requestKey;/,
  'cloud web share list reads must block duplicate same-scope requests',
);
assert.match(
  useDriveShares,
  /const nextShareLinks = await fetchMyShareLinks\(authToken\);[\s\S]*if \(!isCurrentShareLinksRequest\(requestId, requestKey\)\) \{[\s\S]*return;[\s\S]*setShareLinks\(nextShareLinks\);[\s\S]*catch \(error\) \{[\s\S]*if \(isCurrentShareLinksRequest\(requestId, requestKey\)\) \{[\s\S]*message\.error[\s\S]*finally \{[\s\S]*if \(isCurrentShareLinksRequest\(requestId, requestKey\)\) \{[\s\S]*shareLinksLoadingKeyRef\.current = null;[\s\S]*setShareLinksLoading\(false\);/,
  'cloud web share list reads must ignore stale responses',
);
assert.equal(
  countMatches(useDriveShares, /loadShareLinks\(\{ force: true \}\)/g),
  2,
  'cloud web share mutations must force share list refresh after successful changes',
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
  /icon=\{<Icon icon=\{RefreshCw\} \/>\}[\s\S]*loading=\{loading\}[\s\S]*disabled=\{loading \|\| shareRevokingId !== null\}[\s\S]*onClick=\{onRefresh\}/,
  'cloud web shares view must not refresh while list loading or revocation is pending',
);
assert.match(
  drivePage,
  /shareRevokingId=\{shares\.shareRevokingId\}/,
  'cloud drive page must wire share revocation pending state to the UI',
);
assert.match(
  driveSharesView,
  /import \{ useRef, useState \} from 'react';[\s\S]*const shareCopyingIdRef = useRef<number \| null>\(null\);[\s\S]*const \[shareCopyingId, setShareCopyingId\] = useState<number \| null>\(null\);/,
  'cloud web shares view must track pending share link copies',
);
assert.match(
  driveSharesView,
  /async function handleCopy\(shareId: number, value: string\) \{[\s\S]*if \(shareCopyingIdRef\.current !== null \|\| shareRevokingId !== null\) \{[\s\S]*return;[\s\S]*shareCopyingIdRef\.current = shareId;[\s\S]*setShareCopyingId\(shareId\);[\s\S]*await copyText\(value\);[\s\S]*finally \{[\s\S]*shareCopyingIdRef\.current = null;[\s\S]*setShareCopyingId\(null\);/,
  'cloud web share link copies must block duplicate submissions',
);
assert.match(
  driveSharesView,
  /const shareCopying = shareCopyingId === share\.id;[\s\S]*loading=\{shareCopying\}[\s\S]*disabled=\{shareRevokingId !== null \|\| \(shareCopyingId !== null && !shareCopying\)\}[\s\S]*onClick=\{\(\) => void handleCopy\(share\.id, resolveShareUrl\(share\.shareCode\)\)\}/,
  'cloud web shares view must surface pending share link copies',
);
assert.match(
  sharePage,
  /import \{ useEffect, useMemo, useRef, useState \} from 'react';[\s\S]*const passwordCheckingRef = useRef\(false\);[\s\S]*const savingRef = useRef\(false\);[\s\S]*const downloadingNodeIdRef = useRef<number \| null>\(null\);[\s\S]*const downloadingSelectionRef = useRef\(false\);[\s\S]*const saveFolderOptionsLoadingRef = useRef\(false\);/,
  'cloud share page must track synchronous pending guards',
);
assert.match(
  sharePage,
  /type ShareMobileOpenAction = 'intent' \| 'download';[\s\S]*const \[mobileOpenAction, setMobileOpenAction\] = useState<ShareMobileOpenAction \| null>\(null\);[\s\S]*const mobileOpenActionRef = useRef<ShareMobileOpenAction \| null>\(null\);/,
  'cloud share page mobile app handoff must track pending navigation',
);
assert.match(
  sharePage,
  /function beginMobileOpenAction\(action: ShareMobileOpenAction\) \{[\s\S]*if \(mobileOpenActionRef\.current !== null\) \{[\s\S]*return false;[\s\S]*mobileOpenActionRef\.current = action;[\s\S]*setMobileOpenAction\(action\);[\s\S]*function openInAndroidApp\(\) \{[\s\S]*if \(!beginMobileOpenAction\('intent'\)\) \{[\s\S]*window\.location\.assign\(buildShareIntentUrl\(normalizedShareCode\)\);[\s\S]*scheduleMobileOpenReset\('intent'\);[\s\S]*function openAppDownloadPage\(\) \{[\s\S]*if \(!beginMobileOpenAction\('download'\)\) \{[\s\S]*window\.location\.assign\(buildAppDownloadUrl\(normalizedShareCode\)\);[\s\S]*scheduleMobileOpenReset\('download'\);/,
  'cloud share page mobile app handoff must block duplicate navigation',
);
assert.match(
  sharePage,
  /setShowMobileOpenHint\(shareCodeValid && isLikelyMobileClient\(\)\);[\s\S]*mobileOpenActionRef\.current = null;[\s\S]*setMobileOpenAction\(null\);[\s\S]*loading=\{mobileOpenAction === 'intent'\}[\s\S]*disabled=\{mobileOpenAction === 'download'\}[\s\S]*loading=\{mobileOpenAction === 'download'\}[\s\S]*disabled=\{mobileOpenAction === 'intent'\}[\s\S]*disabled=\{mobileOpenAction !== null\}[\s\S]*onClick=\{hideMobileOpenHint\}/,
  'cloud share page mobile app handoff controls must surface pending navigation',
);
assert.match(
  sharePage,
  /async function handlePasswordSubmit\(values: VerifySharePasswordPayload\) \{[\s\S]*if \(passwordCheckingRef\.current\) \{[\s\S]*return;[\s\S]*passwordCheckingRef\.current = true;[\s\S]*setPasswordChecking\(true\);[\s\S]*await verifySharePassword\(normalizedShareCode, values\);[\s\S]*finally \{[\s\S]*passwordCheckingRef\.current = false;[\s\S]*setPasswordChecking\(false\);/,
  'cloud share page password check must block duplicate submissions',
);
assert.match(
  sharePage,
  /async function loadSaveFolderOptions\(\) \{[\s\S]*if \(!authToken \|\| saveFolderOptionsLoadingRef\.current\) \{[\s\S]*saveFolderOptionsLoadingRef\.current = true;[\s\S]*setSaveFolderOptionsLoading\(true\);[\s\S]*finally \{[\s\S]*saveFolderOptionsLoadingRef\.current = false;[\s\S]*setSaveFolderOptionsLoading\(false\);[\s\S]*function closeSaveTargetModal\(\) \{[\s\S]*if \(savingRef\.current\) \{[\s\S]*function openSaveTargetModal\(\) \{[\s\S]*if \(!authToken \|\| !detail \|\| savingRef\.current \|\| downloadingSelectionRef\.current \|\| downloadingNodeIdRef\.current !== null\) \{[\s\S]*async function handleSaveShare\(\) \{[\s\S]*if \(!authToken \|\| !detail \|\| savingRef\.current \|\| downloadingSelectionRef\.current \|\| downloadingNodeIdRef\.current !== null\) \{[\s\S]*savingRef\.current = true;[\s\S]*setSaving\(true\);[\s\S]*await saveShareToDrive\([\s\S]*finally \{[\s\S]*savingRef\.current = false;[\s\S]*setSaving\(false\);/,
  'cloud share page save flow must block duplicate submissions and pending close',
);
assert.match(
  sharePage,
  /async function handleDownloadFile\(item: ShareTreeNode\) \{[\s\S]*if \(!authToken \|\| !detail \|\| savingRef\.current \|\| downloadingSelectionRef\.current \|\| downloadingNodeIdRef\.current !== null\) \{[\s\S]*downloadingNodeIdRef\.current = item\.id;[\s\S]*setDownloadingNodeId\(item\.id\);[\s\S]*finally \{[\s\S]*downloadingNodeIdRef\.current = null;[\s\S]*setDownloadingNodeId\(null\);[\s\S]*async function handleDownloadArchive\(nodeIds: number\[], busyNodeId: number \| null = null\) \{[\s\S]*if \(!authToken \|\| !detail \|\| savingRef\.current \|\| downloadingSelectionRef\.current \|\| downloadingNodeIdRef\.current !== null\) \{[\s\S]*downloadingSelectionRef\.current = true;[\s\S]*setDownloadingSelection\(true\);[\s\S]*downloadingNodeIdRef\.current = busyNodeId;[\s\S]*setDownloadingNodeId\(busyNodeId\);[\s\S]*finally \{[\s\S]*downloadingSelectionRef\.current = false;[\s\S]*setDownloadingSelection\(false\);[\s\S]*downloadingNodeIdRef\.current = null;[\s\S]*setDownloadingNodeId\(null\);/,
  'cloud share page downloads must block competing submissions',
);
assert.match(
  sharePage,
  /loading=\{downloadingNodeId === item\.id\}[\s\S]*disabled=\{saving \|\| downloadingSelection \|\| \(downloadingNodeId !== null && downloadingNodeId !== item\.id\)\}/,
  'cloud share page row downloads must surface pending state',
);
assert.match(
  sharePage,
  /<Form form=\{passwordForm\} layout="vertical" disabled=\{passwordChecking\}[\s\S]*loading=\{passwordChecking\} disabled=\{passwordChecking\}/,
  'cloud share page password form must surface pending state',
);
assert.match(
  sharePage,
  /loading=\{downloadingSelection\}[\s\S]*disabled=\{selectedShareRootNodeIds\.length === 0 \|\| saving \|\| downloadingNodeId !== null\}[\s\S]*loading=\{saving\}[\s\S]*disabled=\{selectedShareRootNodeIds\.length === 0 \|\| saving \|\| downloadingSelection \|\| downloadingNodeId !== null\}[\s\S]*getCheckboxProps: \(\) => \(\{ disabled: saving \|\| downloadingSelection \|\| downloadingNodeId !== null \}\)/,
  'cloud share page toolbar actions must surface pending save and download state',
);
assert.match(
  sharePage,
  /onCancel=\{closeSaveTargetModal\}[\s\S]*confirmLoading=\{saving\}[\s\S]*maskClosable=\{!saving\}[\s\S]*closable=\{!saving\}[\s\S]*cancelButtonProps=\{\{ disabled: saving \}\}/,
  'cloud share page save modal must block pending close',
);
assert.match(
  driveShareCreateModal,
  /import \{ useEffect, useMemo, useRef, useState \} from 'react';[\s\S]*type ShareCopyAction = 'link' \| 'password';[\s\S]*const copyActionRef = useRef<ShareCopyAction \| null>\(null\);[\s\S]*const \[copyAction, setCopyAction\] = useState<ShareCopyAction \| null>\(null\);/,
  'cloud share create modal must track pending copy actions',
);
assert.match(
  driveShareCreateModal,
  /function closeModal\(\) \{[\s\S]*if \(copyActionRef\.current !== null\) \{[\s\S]*return;[\s\S]*onClose\(\);[\s\S]*async function handleCopy\(action: ShareCopyAction, value: string, successText: string\) \{[\s\S]*if \(copyActionRef\.current !== null\) \{[\s\S]*copyActionRef\.current = action;[\s\S]*setCopyAction\(action\);[\s\S]*await copyText\(value\);[\s\S]*finally \{[\s\S]*copyActionRef\.current = null;[\s\S]*setCopyAction\(null\);/,
  'cloud share create modal copy actions must block duplicate submissions and pending close',
);
assert.match(
  driveShareCreateModal,
  /const copyPending = copyAction !== null;[\s\S]*onCancel=\{closeModal\}[\s\S]*closable=\{!creating && !copyPending\}[\s\S]*maskClosable=\{!creating && !copyPending\}[\s\S]*keyboard=\{!creating && !copyPending\}[\s\S]*cancelButtonProps=\{\{ disabled: creating \|\| copyPending \}\}[\s\S]*disabled=\{copyPending\}[\s\S]*onClick=\{closeModal\}[\s\S]*loading=\{copyAction === 'link'\}[\s\S]*disabled=\{copyAction === 'password'\}[\s\S]*loading=\{copyAction === 'password'\}[\s\S]*disabled=\{copyAction === 'link'\}/,
  'cloud share create modal copy controls must surface pending state',
);
assert.match(
  useDriveDownloads,
  /function commitDownloadTasks\(updater: \(tasks: DriveDownloadTask\[]\) => DriveDownloadTask\[]\) \{[\s\S]*const nextTasks = updater\(downloadTasksRef\.current\);[\s\S]*downloadTasksRef\.current = nextTasks;[\s\S]*setDownloadTasksState\(nextTasks\);/,
  'cloud web download tasks must update the synchronous task ref before React state',
);
assert.match(
  useDriveDownloads,
  /function isCancelableDownloadStatus\(status: DriveDownloadTaskStatus\) \{[\s\S]*return status === 'queued' \|\| status === 'preparing' \|\| status === 'downloading';/,
  'cloud web download cancellation must only target cancelable transfer states',
);
assert.match(
  useDriveDownloads,
  /onProgress: \(\{ loaded, total, percent \}\) => \{[\s\S]*if \(controller\.signal\.aborted\) \{[\s\S]*return;[\s\S]*if \(controller\.signal\.aborted\) \{[\s\S]*throw createAbortError\(\);[\s\S]*if \(isAbortError\(downloadError\) \|\| controller\.signal\.aborted\) \{/,
  'cloud web download progress must ignore aborted transfers',
);
assert.match(
  useDriveDownloads,
  /function cancelDownloadTask\(taskId: string\) \{[\s\S]*const task = downloadTasksRef\.current\.find[\s\S]*if \(!task \|\| !isCancelableDownloadStatus\(task\.status\)\) \{[\s\S]*const controller = controllersRef\.current\.get\(taskId\);[\s\S]*controller\.abort\(\);[\s\S]*status: 'canceled'/,
  'cloud web download cancellation must synchronously mark tasks canceled',
);
assert.match(
  driveDownloadsView,
  /const CANCELABLE_DOWNLOAD_STATUSES = new Set<DriveDownloadTaskStatus>\(\[[\s\S]*'queued',[\s\S]*'preparing',[\s\S]*'downloading',[\s\S]*function isCancelableTask\(task: DriveDownloadTask\)[\s\S]*const cancelable = isCancelableTask\(task\);[\s\S]*\{cancelable \? \(/,
  'cloud web downloads view must hide cancel for non-cancelable download states',
);
assert.match(
  useDriveExplorer,
  /type DriveListLoadOptions = \{[\s\S]*force\?: boolean;[\s\S]*const listRequestIdRef = useRef\(0\);[\s\S]*const listLoadingKeyRef = useRef<string \| null>\(null\);/,
  'cloud web list reads must track request identity',
);
assert.match(
  useDriveExplorer,
  /function createListRequestKey\(\) \{[\s\S]*return JSON\.stringify\(\[[\s\S]*authToken,[\s\S]*activeView,[\s\S]*currentFolderId,[\s\S]*fileCategory,[\s\S]*keyword,[\s\S]*nodeTypeFilter,[\s\S]*listState\.sortDirection,[\s\S]*function isCurrentListRequest\(requestId: number, requestKey: string\) \{[\s\S]*listRequestIdRef\.current === requestId && listLoadingKeyRef\.current === requestKey;/,
  'cloud web list reads must compare the full visible list scope',
);
assert.match(
  useDriveExplorer,
  /async function loadDrive\(options: DriveListLoadOptions = \{\}\) \{[\s\S]*if \(!authToken \|\| !isListView\) \{[\s\S]*listRequestIdRef\.current \+= 1;[\s\S]*listLoadingKeyRef\.current = null;[\s\S]*const requestKey = createListRequestKey\(\);[\s\S]*if \(!options\.force && listLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*listRequestIdRef\.current \+= 1;[\s\S]*const requestId = listRequestIdRef\.current;[\s\S]*listLoadingKeyRef\.current = requestKey;/,
  'cloud web list reads must block duplicate same-scope requests',
);
assert.match(
  useDriveExplorer,
  /const nodeData = await nodeRequest;[\s\S]*if \(!isCurrentListRequest\(requestId, requestKey\)\) \{[\s\S]*return;[\s\S]*setItems\(nodeData\.items\);[\s\S]*catch \(loadError\) \{[\s\S]*if \(isCurrentListRequest\(requestId, requestKey\)\) \{[\s\S]*setError\([\s\S]*finally \{[\s\S]*if \(isCurrentListRequest\(requestId, requestKey\)\) \{[\s\S]*listLoadingKeyRef\.current = null;[\s\S]*setLoading\(false\);/,
  'cloud web list reads must ignore stale responses',
);
assert.equal(
  countMatches(useDriveExplorer, /loadDrive\(\{ force: true \}\)/g),
  7,
  'cloud web storage mutations must force list refresh after successful changes',
);
assert.match(
  driveTypes,
  /DriveStorageMutationKind = 'create-folder' \| 'rename' \| 'move' \| 'delete' \| 'restore' \| 'permanent-delete'/,
  'cloud web storage mutation state must cover all personal file mutations',
);
assert.match(
  useDriveExplorer,
  /const \[storageMutation, setStorageMutation\] = useState<DriveStorageMutationState>\(null\);[\s\S]*const storageMutationRef = useRef<DriveStorageMutationState>\(null\);/,
  'cloud web storage mutations must track a single pending operation',
);
assert.match(
  useDriveExplorer,
  /function beginStorageMutation\(kind: DriveStorageMutationKind, nodeIds: number\[]\)[\s\S]*if \(storageMutationRef\.current !== null\) \{[\s\S]*return false;[\s\S]*setStorageMutation\(nextStorageMutation\);/,
  'cloud web storage mutations must block duplicate submissions',
);
assert.equal(
  countMatches(useDriveExplorer, /clearStorageMutation\(\);/g),
  6,
  'cloud web storage mutations must clear pending state after each personal file mutation',
);
assert.match(
  useDriveStorageDialogs,
  /storageMutation: DriveStorageMutationState[\s\S]*function closeCreateFolderModal\(\) \{[\s\S]*if \(storageMutation\) \{[\s\S]*return;[\s\S]*function submitMove\(values: MoveNodeFormValues\) \{[\s\S]*if \(storageMutation\) \{/,
  'cloud web storage dialogs must block close and submit while a storage mutation is pending',
);
assert.match(
  driveStorageActionModals,
  /confirmLoading=\{createFolderSaving\}[\s\S]*disabled=\{createFolderSaving\}[\s\S]*confirmLoading=\{renameSaving\}[\s\S]*disabled=\{renameSaving\}[\s\S]*confirmLoading=\{moveSaving\}[\s\S]*disabled=\{folderOptionsLoading \|\| moveSaving\}/,
  'cloud web storage modals must surface pending create, rename, and move submissions',
);
assert.match(
  driveExplorerView,
  /const storageMutationPending = storageMutation !== null;[\s\S]*loading=\{creatingFolder\}[\s\S]*disabled=\{storageMutationPending \|\| refreshPending\}[\s\S]*loading=\{restoringSelection\}[\s\S]*loading=\{movingSelection\}[\s\S]*loading=\{deletingSelection\}/,
  'cloud web explorer toolbar must surface pending storage mutations',
);
assert.match(
  storageTable,
  /storageMutation: DriveStorageMutationState[\s\S]*function isNodeStorageMutating\(item: StorageNode, kind: DriveStorageMutationKind\)[\s\S]*loading=\{restoring\}[\s\S]*loading=\{permanentlyDeleting\}[\s\S]*loading=\{renaming\}[\s\S]*loading=\{moving\}[\s\S]*loading=\{deleting\}[\s\S]*getCheckboxProps: \(\) => \(\{ disabled: storageMutation !== null \}\)/,
  'cloud web storage table must surface pending row mutations and freeze selection',
);
assert.match(
  drivePage,
  /storageMutation: explorer\.storageMutation[\s\S]*const storageMutation = explorer\.storageMutation;[\s\S]*storageMutation=\{storageMutation\}/,
  'cloud drive page must wire storage mutation pending state to dialogs and tables',
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
