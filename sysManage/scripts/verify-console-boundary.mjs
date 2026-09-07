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
  { pattern: /\b(IdentityAudit|IdentityApplicationRole|CreateIdentityUser|ResetIdentityUserPassword|UpdateIdentityApplicationRole)[A-Za-z0-9_]*\b/, reason: 'identity admin types belong in mainSite/userSite' },
  { pattern: /\b(fetchIdentityUsers|createIdentityUser|resetIdentityUserPassword|fetchIdentityApplicationRoles|updateIdentityApplicationRole|fetchIdentityAuditLogs)\b/, reason: 'identity admin API calls belong in mainSite/userSite' },
  { pattern: /\b(StorageViewMode|StorageFileCategory|StorageNodeFilter|StorageNodeSortField)\b/, reason: 'personal drive types belong in webApp' },
  { pattern: /\b(fetchIdentitySessions|revokeIdentitySession|changePassword)\b/, reason: 'personal session and password settings belong in webApp or userSite' },
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

function extractForbiddenRoutePathLiterals(fileUrl) {
  const source = readFileSync(fileUrl, 'utf8');
  const routePathPattern = /(['"`])(\/(?:console\/identity|rag)(?:[^'"`]*)?)\1/g;
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
    ['/api', '/api/', '/console/identity', '/console/identity/', '/rag', '/rag/'].includes(path)
  );
}

function assertNoForbiddenRoutePathLiterals(fileUrls, label) {
  const violations = fileUrls
    .flatMap((fileUrl) => extractForbiddenRoutePathLiterals(fileUrl))
    .filter(({ path, relativePath }) => !isReturnPathBoundarySentinel(relativePath, path));

  assert.deepEqual(
    violations,
    [],
    `${label} must not expose identity console or RAG routes from the cloud console:\n${violations
      .map(({ relativePath, line, path }) => `  ${relativePath}:${line} ${path}`)
      .join('\n')}`,
  );
}

function assertIncludesInOrder(source, snippets, message) {
  let searchFrom = 0;

  for (const snippet of snippets) {
    const foundAt = source.indexOf(snippet, searchFrom);
    assert.notEqual(foundAt, -1, `${message}: missing ${snippet}`);
    searchFrom = foundAt + snippet.length;
  }
}

function findMatchingBrace(source, openIndex) {
  let depth = 0;

  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1;
    } else if (source[index] === '}') {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  throw new Error('No matching } found.');
}

function extractApiFunctionBody(functionName) {
  const functionPattern = new RegExp(`export\\s+function\\s+${functionName}\\s*\\(`);
  const match = functionPattern.exec(apiSource);
  assert.ok(match, `${functionName} must exist in sysManage/src/lib/api.ts`);

  const openIndex = apiSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${functionName} must use a function body`);

  return apiSource.slice(openIndex + 1, findMatchingBrace(apiSource, openIndex));
}

const files = listSourceFiles(srcRoot);
const cloudConsoleApiScopeFiles = files;
const cloudConsoleAllowedApiPrefixes = [
  '/api/health',
  '/api/cloud-profile/me',
  '/api/cloud-profile/avatar',
  '/api/identity/auth/profile',
  '/api/identity/auth/token/refresh',
  '/api/identity/auth/logout',
  '/api/admin/cloud-users',
  '/api/admin/cloud-operations',
  '/api/admin/app-package',
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
  cloudConsoleApiScopeFiles,
  cloudConsoleAllowedApiPrefixes,
  'cloud console source',
);
assertNoForbiddenRoutePathLiterals(files, 'cloud console source');

const consolePageSource = readFileSync(new URL('../src/pages/CloudConsolePage.tsx', import.meta.url), 'utf8');
const cloudUsersViewSource = readFileSync(new URL('../src/features/drive/CloudUsersView.tsx', import.meta.url), 'utf8');
const driveOperationsViewSource = readFileSync(new URL('../src/features/drive/DriveOperationsView.tsx', import.meta.url), 'utf8');
const driveAppPackageViewSource = readFileSync(new URL('../src/features/drive/DriveAppPackageView.tsx', import.meta.url), 'utf8');
const appPackageUploadModalSource = readFileSync(new URL('../src/features/drive/DriveAppPackageUploadModal.tsx', import.meta.url), 'utf8');
const driveSharedSource = readFileSync(new URL('../src/features/drive/driveShared.ts', import.meta.url), 'utf8');
const appPackagePanelSource = readFileSync(new URL('../src/components/AppPackagePanel.tsx', import.meta.url), 'utf8');
const consoleStyles = readFileSync(new URL('../src/index.css', import.meta.url), 'utf8');
const appSource = readFileSync(new URL('../src/App.tsx', import.meta.url), 'utf8');
const apiSource = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8');
const cloudUsersHookSource = readFileSync(new URL('../src/features/drive/hooks/useCloudUsersAdmin.ts', import.meta.url), 'utf8');
const driveOperationsHookSource = readFileSync(new URL('../src/features/drive/hooks/useDriveOperationsAdmin.ts', import.meta.url), 'utf8');
const appPackageHookSource = readFileSync(new URL('../src/features/drive/hooks/useDriveAppPackageAdmin.ts', import.meta.url), 'utf8');
const typesSource = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8');
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
assert.match(typesSource, /export function isCloudAdmin/, 'cloud console must centralize its runtime access predicate');
assert.match(typesSource, /export function cloudRoleLabel/, 'cloud console must centralize its role label copy');
assert.match(
  typesSource,
  /return user\?\.role === 'ADMIN' \|\| user\?\.appRoles\?\.cloud === 'CLOUD_ADMIN';/,
  'cloud console runtime access must accept global admins and cloud application admins',
);
assert.match(typesSource, /return '全局管理员';/, 'cloud console role label must distinguish global administrators');
assert.match(typesSource, /return '云盘管理员';/, 'cloud console role label must distinguish cloud application administrators');
assert.match(
  consolePageSource,
  /const isAdmin = isCloudAdmin\(currentUser\);/,
  'cloud console page must use the centralized cloud admin predicate',
);
assert.match(
  consolePageSource,
  /cloudRoleLabel\(currentUser\)/,
  'cloud console sidebar must use the centralized cloud role label copy',
);
assert.match(
  consolePageSource,
  /activeViewContent = !isAdmin \? \([\s\S]*title="没有云盘后台权限"/,
  'cloud console must show its permission denied state before rendering admin views',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchUsers(token: string)',
    "requestJson<User[]>('/api/admin/cloud-users'",
  ],
  'cloud console users view must load CloudStorageApi cloud-users',
);
assert.doesNotMatch(
  apiSource,
  /requestJson<[^>]+>\(\s*['"`]\/api\/admin\/cloud-users['"`][\s\S]*?method:\s*'POST'/,
  'cloud console user directory must not expose identity user creation from sysManage',
);
assert.doesNotMatch(
  cloudUsersViewSource,
  /新增用户|创建用户|重置密码|Input\.Password|name="password"|inheritAdminBackground/,
  'cloud console users view must keep identity account creation in the identity console',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function updateUserStorageQuota(userId: number, payload: UpdateUserStorageQuotaPayload, token: string)',
    '`/api/admin/cloud-users/${userId}/quota`',
    "method: 'PUT'",
    'body: JSON.stringify(payload)',
  ],
  'cloud console quota mutations must use CloudStorageApi cloud-users quota contract',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'function openQuotaModal(user: User) {',
    'quotaForm.setFieldsValue({',
    'storageQuotaGb: bytesToGigabytes(user.storageQuotaBytes),',
  ],
  'cloud console quota modal must present backend byte quotas as GiB',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'const storageQuotaBytes = gigabytesToBytes(values.storageQuotaGb);',
    'if (storageQuotaBytes < target.usedBytes) {',
    'const updatedUser = await updateUserStorageQuota(target.id, { storageQuotaBytes }, requestToken);',
  ],
  'cloud console quota submit must convert GiB input to backend bytes',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'if (storageQuotaBytes < target.usedBytes) {',
    '最大额度不能低于当前已用空间',
    'return;',
    'const updatedUser = await updateUserStorageQuota(',
  ],
  'cloud console quota submit must reject quotas below current usage',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'function closeQuotaModal() {',
    'if (quotaSavingRef.current) {',
    'return;',
    'resetQuotaModal();',
  ],
  'cloud console quota modal close must respect pending quota submissions',
);
assertIncludesInOrder(
  cloudUsersViewSource,
  [
    'confirmLoading={quotaSaving}',
    'maskClosable={!quotaSaving}',
    'closable={!quotaSaving}',
    'cancelButtonProps={{ disabled: quotaSaving }}',
    'disabled={quotaSaving}',
  ],
  'cloud console quota modal must block duplicate submissions',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'const [quotaSaving, setQuotaSaving] = useState(false);',
    'const quotaSavingRef = useRef(false);',
    'function openQuotaModal(user: User) {',
    'if (quotaSavingRef.current) {',
    'async function submitQuotaUpdate() {',
    'const target = quotaTarget;',
    'if (!authToken || !target || !isAdmin || quotaSavingRef.current) {',
    'quotaSavingRef.current = true;',
    'setQuotaSaving(true);',
    'const values = await quotaForm.validateFields();',
    'const updatedUser = await updateUserStorageQuota(',
    'if (typeof saveError ===',
    'errorFields',
    'return;',
    '} finally {',
    'quotaSavingRef.current = false;',
    'setQuotaSaving(false);',
  ],
  'cloud console quota updates must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'function createCloudUserMutationRequestKey(',
    'return JSON.stringify([scope, token, admin, target]);',
    'function isCurrentCloudUserMutationRequest(requestKey: string, scope: string, target: unknown = null)',
    'return createCloudUserMutationRequestKey(scope, authTokenRef.current, isAdminRef.current, target) === requestKey;',
    'async function submitQuotaUpdate() {',
    'const target = quotaTarget;',
    'const requestToken = authToken;',
    "const requestKey = createCloudUserMutationRequestKey('quota', requestToken, isAdmin, target.id);",
    'const updatedUser = await updateUserStorageQuota(target.id, { storageQuotaBytes }, requestToken);',
    "if (!isCurrentCloudUserMutationRequest(requestKey, 'quota', target.id)) {",
    "message.success('已更新用户云盘额度。');",
    "if (isCurrentCloudUserMutationRequest(requestKey, 'quota', target.id)) {",
  ],
  'cloud console quota mutations must ignore stale auth admin scope',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchAdminCloudOperationsOverview(token: string)',
    "requestJson<AdminCloudOperationsOverview>('/api/admin/cloud-operations/overview'",
  ],
  'cloud console operations view must load CloudStorageApi operations overview',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchAdminCloudOperationShares(query: AdminCloudShareLinksQuery, token: string)',
    'appendAdminOperationPageParams(search, query);',
    '`/api/admin/cloud-operations/shares${toQuerySuffix(search)}`',
  ],
  'cloud console operations view must load CloudStorageApi share operations',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchAdminCloudOperationTrash(query: AdminCloudTrashNodesQuery, token: string)',
    'appendAdminOperationPageParams(search, query);',
    '`/api/admin/cloud-operations/trash${toQuerySuffix(search)}`',
  ],
  'cloud console operations view must load CloudStorageApi trash operations',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchAdminCloudStorageUsers(query: AdminCloudStorageUsersQuery, token: string)',
    'appendAdminOperationPageParams(search, query);',
    '`/api/admin/cloud-operations/users/storage${toQuerySuffix(search)}`',
  ],
  'cloud console operations view must load CloudStorageApi storage user operations',
);
for (const functionName of [
  'fetchAdminCloudOperationsOverview',
  'fetchAdminCloudOperationShares',
  'fetchAdminCloudOperationTrash',
  'fetchAdminCloudStorageUsers',
]) {
  assert.doesNotMatch(
    extractApiFunctionBody(functionName),
    /method:\s*'(?:POST|PUT|PATCH|DELETE)'/,
    'cloud console operations APIs must stay read-only GET contracts',
  );
}
assert.doesNotMatch(
  driveOperationsViewSource,
  /title:\s*'操作'|onRestore|onDelete|onRevoke|restore[A-Z][A-Za-z0-9_]*|delete[A-Z][A-Za-z0-9_]*|revoke[A-Z][A-Za-z0-9_]*/,
  'cloud console operations view must not expose personal file mutation controls',
);
assert.doesNotMatch(
  driveOperationsHookSource,
  /\b(?:update|delete|restore|revoke|permanentlyDelete|createShareLink|uploadStorageFile)[A-Za-z0-9_]*\(/,
  'cloud console operations hook must not import personal file mutation flows',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchPublicAppPackage()',
    "requestJson<AppPackageInfo>('/api/app-package')",
  ],
  'cloud console APK view may read the public CloudStorageApi app package contract',
);
assert.match(
  driveSharedSource,
  /export function normalizeAppDownloadPath\(downloadPath = APP_DOWNLOAD_PUBLIC_PATH\)/,
  'cloud console app package download URL resolver must normalize download paths',
);
assert.match(
  driveSharedSource,
  /url\.origin !== currentOrigin \|\| url\.pathname !== APP_DOWNLOAD_PUBLIC_PATH/,
  'cloud console app package download URL resolver must stay on the public package endpoint',
);
assert.match(
  appPackagePanelSource,
  /const downloadUrl = normalizeAppDownloadPath\(packageInfo\?\.downloadUrl\);/,
  'cloud console app package panel must use the normalized public download path',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function fetchAdminAppPackage(token: string)',
    "requestJson<AppPackageInfo>('/api/admin/app-package'",
  ],
  'cloud console APK view must load CloudStorageApi admin app package',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function uploadAdminAppPackage(',
    "requestUploadJson<AppPackageInfo>('/api/admin/app-package'",
  ],
  'cloud console APK upload must use CloudStorageApi admin app package',
);
assert.match(
  appPackageUploadModalSource,
  /accept="\.apk,application\/vnd\.android\.package-archive"/,
  'cloud console APK upload modal must restrict picker to Android package files',
);
assertIncludesInOrder(
  appPackageUploadModalSource,
  [
    'name="versionName"',
    "{ required: true, message: '请填写更新版本。' }",
    "{ max: 64, message: '更新版本长度不能超过 64 个字符。' }",
  ],
  'cloud console APK upload modal must require a bounded version name',
);
assertIncludesInOrder(
  appPackageUploadModalSource,
  [
    'name="releaseNotes"',
    "{ required: true, message: '请填写更新说明。' }",
    "{ max: 4000, message: '更新说明长度不能超过 4000 个字符。' }",
  ],
  'cloud console APK upload modal must require bounded release notes',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'function handleAppPackageFileChange(event: ChangeEvent<HTMLInputElement>)',
    "event.target.value = '';",
    "if (!selectedFile.name.toLowerCase().endsWith('.apk'))",
    "message.error('请上传 APK 安装包文件。');",
    'setSelectedAppPackageFile(selectedFile);',
  ],
  'cloud console APK upload hook must reject non-APK files before storing the draft',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'const nextPackageInfo = await uploadAdminAppPackage(',
    'values.versionName.trim(),',
    'values.releaseNotes.trim(),',
    'setAppPackageInfo(nextPackageInfo);',
    'setPublicAppPackageInfo(nextPackageInfo);',
    'setPublicAppPackageError(null);',
  ],
  'cloud console APK upload must refresh admin and public package state together',
);
assertIncludesInOrder(
  apiSource,
  [
    'export function deleteAdminAppPackage(token: string)',
    "'/api/admin/app-package'",
    "method: 'DELETE'",
  ],
  'cloud console APK deletion must use CloudStorageApi admin app package',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'await deleteAdminAppPackage(requestToken);',
    'setAppPackageInfo(createEmptyAppPackageInfo());',
    'setPublicAppPackageInfo(createEmptyAppPackageInfo());',
    'setPublicAppPackageError(null);',
  ],
  'cloud console APK delete must clear admin and public package state together',
);
assertIncludesInOrder(
  consolePageSource,
  [
    'const operationsLoading =',
    'const appPackageBusy =',
    'const activeViewLoading =',
    'async function refreshCurrentView() {',
    'if (!isAdmin || activeViewLoading) {',
    'return;',
    "if (activeView === 'users')",
    'await cloudUsers.loadUsers({ force: true });',
    "if (activeView === 'operations')",
    'await operations.loadAll({ force: true });',
    'await appPackages.loadAppPackageState({ force: true });',
  ],
  'cloud console header refresh must not load admin view data without cloud admin access',
);
assertIncludesInOrder(
  consolePageSource,
  [
    'const operationsLoading =',
    'const appPackageBusy =',
    'const activeViewLoading =',
    'disabled={activeViewLoading}',
    'onClick={() => void refreshCurrentView()}',
    'loading={activeViewLoading}',
  ],
  'cloud console header refresh must pause duplicate active-view refreshes',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'async function loadUsers(options: CloudUsersLoadOptions = {}) {',
    'if (!authToken || !isAdmin) {',
    'setUsers([]);',
    'const nextUsers = await fetchUsers(authToken);',
    'setUsers(nextUsers);',
  ],
  'cloud console users hook must keep cloud-users reads behind the cloud admin gate',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'const usersLoadingRef = useRef(false);',
    'const usersRequestIdRef = useRef(0);',
    'const usersLoadingKeyRef = useRef<string | null>(null);',
    'async function loadUsers(options: CloudUsersLoadOptions = {}) {',
    'if (!options.force && usersLoadingKeyRef.current === requestKey) {',
    'usersLoadingRef.current = true;',
    'setUsersLoading(true);',
    '} finally {',
    'usersLoadingKeyRef.current = null;',
    'usersLoadingRef.current = false;',
    'setUsersLoading(false);',
  ],
  'cloud console users reads must keep synchronous loading guards',
);
assert.match(
  cloudUsersHookSource,
  /type CloudUsersLoadOptions = \{[\s\S]*force\?: boolean;[\s\S]*const authTokenRef = useRef\(authToken\);[\s\S]*const isAdminRef = useRef\(isAdmin\);[\s\S]*const usersRequestIdRef = useRef\(0\);[\s\S]*const usersLoadingKeyRef = useRef<string \| null>\(null\);/,
  'cloud console users reads must track request identity',
);
assert.match(
  cloudUsersHookSource,
  /function createUsersRequestKey\(token: string \| null = authToken, admin = isAdmin\) \{[\s\S]*return JSON\.stringify\(\[token, admin\]\);[\s\S]*function isCurrentUsersRequest\(requestId: number, requestKey: string\) \{[\s\S]*usersRequestIdRef\.current === requestId[\s\S]*usersLoadingKeyRef\.current === requestKey[\s\S]*createUsersRequestKey\(authTokenRef\.current, isAdminRef\.current\) === requestKey/,
  'cloud console users reads must compare auth admin scope',
);
assert.match(
  cloudUsersHookSource,
  /async function loadUsers\(options: CloudUsersLoadOptions = \{\}\) \{[\s\S]*if \(!authToken \|\| !isAdmin\) \{[\s\S]*usersRequestIdRef\.current \+= 1;[\s\S]*usersLoadingKeyRef\.current = null;[\s\S]*const requestKey = createUsersRequestKey\(authToken, isAdmin\);[\s\S]*if \(!options\.force && usersLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*usersRequestIdRef\.current \+= 1;[\s\S]*const requestId = usersRequestIdRef\.current;[\s\S]*const nextUsers = await fetchUsers\(authToken\);[\s\S]*if \(!isCurrentUsersRequest\(requestId, requestKey\)\) \{[\s\S]*return;[\s\S]*setUsers\(nextUsers\);[\s\S]*finally \{[\s\S]*if \(isCurrentUsersRequest\(requestId, requestKey\)\) \{[\s\S]*usersLoadingKeyRef\.current = null;/,
  'cloud console users reads must block duplicate reads and ignore stale responses',
);
assert.match(
  cloudUsersHookSource,
  /useEffect\(\(\) => \{[\s\S]*usersRequestIdRef\.current \+= 1;[\s\S]*usersLoadingKeyRef\.current = null;[\s\S]*usersLoadingRef\.current = false;[\s\S]*setUsersLoading\(false\);[\s\S]*setUsers\(\[\]\);[\s\S]*\}, \[authToken, isAdmin\]\);/,
  'cloud console users reads must invalidate auth admin scope changes',
);
assert.match(
  `${consolePageSource}\n${cloudUsersHookSource}`,
  /await cloudUsers\.loadUsers\(\{ force: true \}\);[\s\S]*await loadUsers\(\{ force: true \}\);/,
  'cloud console users refreshes must force reads after header refresh or quota mutations',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'function openQuotaModal(user: User) {',
    'if (!authToken || !isAdmin) {',
    'return;',
    'setQuotaTarget(user);',
  ],
  'cloud console quota modal must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'function openQuotaModal(user: User) {',
    'if (usersLoadingRef.current || quotaSavingRef.current) {',
    'return;',
    'setQuotaTarget(user);',
  ],
  'cloud console quota modal must pause during user refreshes',
);
assertIncludesInOrder(
  cloudUsersViewSource,
  [
    'const usersBusy = loading || quotaSaving;',
    'const adjustingThisUser = quotaSaving && quotaTarget?.id === user.id;',
    'disabled={usersBusy || user.storageQuotaBytes === null}',
    'loading={adjustingThisUser}',
    'loading={loading}',
    'disabled={loading}',
    'pagination={{ pageSize: 10, showSizeChanger: true, disabled: loading }}',
  ],
  'cloud console users controls must surface pending loading state',
);
assertIncludesInOrder(
  cloudUsersHookSource,
  [
    'async function submitQuotaUpdate() {',
    'const target = quotaTarget;',
    'if (!authToken || !target || !isAdmin || quotaSavingRef.current) {',
    'const updatedUser = await updateUserStorageQuota(',
  ],
  'cloud console quota mutations must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadOverview(options: DriveOperationsReadOptions = {}) {',
    'if (!authToken || !isAdmin) {',
    'setOverview(null);',
    'const nextOverview = await fetchAdminCloudOperationsOverview(authToken);',
    'setOverview(nextOverview);',
  ],
  'cloud console operations overview must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'import { useEffect, useRef, useState } from',
    'const overviewLoadingRef = useRef(false);',
    'const storageUsersLoadingRef = useRef(false);',
    'const trashNodesLoadingRef = useRef(false);',
    'const shareLinksLoadingRef = useRef(false);',
    'const overviewRequestIdRef = useRef(0);',
    'const overviewLoadingKeyRef = useRef<string | null>(null);',
    'const storageUsersRequestIdRef = useRef(0);',
    'const storageUsersLoadingKeyRef = useRef<string | null>(null);',
    'const trashNodesRequestIdRef = useRef(0);',
    'const trashNodesLoadingKeyRef = useRef<string | null>(null);',
    'const shareLinksRequestIdRef = useRef(0);',
    'const shareLinksLoadingKeyRef = useRef<string | null>(null);',
    'async function loadOverview(options: DriveOperationsReadOptions = {}) {',
    'if (!options.force && overviewLoadingKeyRef.current === requestKey) {',
    'overviewLoadingRef.current = true;',
    'setOverviewLoading(true);',
    '} finally {',
    'overviewLoadingKeyRef.current = null;',
    'overviewLoadingRef.current = false;',
    'setOverviewLoading(false);',
    'async function loadStorageUsers(',
    'if (!options.force && storageUsersLoadingKeyRef.current === requestKey) {',
    'storageUsersLoadingRef.current = true;',
    'setStorageUsersLoading(true);',
    '} finally {',
    'storageUsersLoadingKeyRef.current = null;',
    'storageUsersLoadingRef.current = false;',
    'setStorageUsersLoading(false);',
    'async function loadTrashNodes(',
    'if (!options.force && trashNodesLoadingKeyRef.current === requestKey) {',
    'trashNodesLoadingRef.current = true;',
    'setTrashNodesLoading(true);',
    '} finally {',
    'trashNodesLoadingKeyRef.current = null;',
    'trashNodesLoadingRef.current = false;',
    'setTrashNodesLoading(false);',
    'async function loadShareLinks(',
    'if (!options.force && shareLinksLoadingKeyRef.current === requestKey) {',
    'shareLinksLoadingRef.current = true;',
    'setShareLinksLoading(true);',
    '} finally {',
    'shareLinksLoadingKeyRef.current = null;',
    'shareLinksLoadingRef.current = false;',
    'setShareLinksLoading(false);',
  ],
  'cloud console operations reads must keep synchronous loading guards',
);
assert.match(
  driveOperationsHookSource,
  /type DriveOperationsReadOptions = \{[\s\S]*force\?: boolean;[\s\S]*type RefState<T> = \{[\s\S]*current: T;[\s\S]*const authTokenRef = useRef\(authToken\);[\s\S]*const isAdminRef = useRef\(isAdmin\);[\s\S]*const overviewRequestIdRef = useRef\(0\);[\s\S]*const storageUsersRequestIdRef = useRef\(0\);[\s\S]*const trashNodesRequestIdRef = useRef\(0\);[\s\S]*const shareLinksRequestIdRef = useRef\(0\);/,
  'cloud console operations reads must track request identity',
);
assert.match(
  driveOperationsHookSource,
  /function createOperationsRequestKey\([\s\S]*scope: string,[\s\S]*token: string \| null = authToken,[\s\S]*admin = isAdmin,[\s\S]*query: unknown = null,[\s\S]*return JSON\.stringify\(\[scope, token, admin, query\]\);[\s\S]*function isCurrentOperationsRequest\([\s\S]*requestIdRef\.current === requestId[\s\S]*loadingKeyRef\.current === requestKey[\s\S]*createOperationsRequestKey\(scope, authTokenRef\.current, isAdminRef\.current, query\) === requestKey/,
  'cloud console operations reads must compare auth admin query scope',
);
assert.match(
  driveOperationsHookSource,
  /async function loadOverview\(options: DriveOperationsReadOptions = \{\}\) \{[\s\S]*if \(!options\.force && overviewLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*const nextOverview = await fetchAdminCloudOperationsOverview\(authToken\);[\s\S]*if \(!isCurrentOperationsRequest\(overviewRequestIdRef, overviewLoadingKeyRef, requestId, requestKey, 'overview'\)\) \{[\s\S]*return;[\s\S]*setOverview\(nextOverview\);[\s\S]*async function loadStorageUsers\([\s\S]*if \(!options\.force && storageUsersLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*const page = await fetchAdminCloudStorageUsers\(query, authToken\);[\s\S]*!isCurrentOperationsRequest\([\s\S]*'storage-users'[\s\S]*setStorageUsersPage\(page\);[\s\S]*async function loadTrashNodes\([\s\S]*if \(!options\.force && trashNodesLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*const page = await fetchAdminCloudOperationTrash\(query, authToken\);[\s\S]*!isCurrentOperationsRequest\([\s\S]*'trash-nodes'[\s\S]*setTrashNodesPage\(page\);[\s\S]*async function loadShareLinks\([\s\S]*if \(!options\.force && shareLinksLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*const page = await fetchAdminCloudOperationShares\(query, authToken\);[\s\S]*!isCurrentOperationsRequest\([\s\S]*'share-links'[\s\S]*setShareLinksPage\(page\);/,
  'cloud console operations reads must block duplicate reads and ignore stale responses',
);
assert.match(
  driveOperationsHookSource,
  /useEffect\(\(\) => \{[\s\S]*overviewRequestIdRef\.current \+= 1;[\s\S]*overviewLoadingKeyRef\.current = null;[\s\S]*setOverview\(null\);[\s\S]*storageUsersRequestIdRef\.current \+= 1;[\s\S]*storageUsersLoadingKeyRef\.current = null;[\s\S]*setStorageUsersPage\(null\);[\s\S]*trashNodesRequestIdRef\.current \+= 1;[\s\S]*trashNodesLoadingKeyRef\.current = null;[\s\S]*setTrashNodesPage\(null\);[\s\S]*shareLinksRequestIdRef\.current \+= 1;[\s\S]*shareLinksLoadingKeyRef\.current = null;[\s\S]*setShareLinksPage\(null\);[\s\S]*\}, \[authToken, isAdmin\]\);/,
  'cloud console operations reads must invalidate auth admin scope changes',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadStorageUsers(',
    'query: AdminCloudStorageUsersQuery = storageUsersQuery,',
    'if (!authToken || !isAdmin) {',
    'setStorageUsersPage(null);',
    'const page = await fetchAdminCloudStorageUsers(query, authToken);',
  ],
  'cloud console storage user operations must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadStorageUsers(',
    'query: AdminCloudStorageUsersQuery = storageUsersQuery,',
    'const page = await fetchAdminCloudStorageUsers(query, authToken);',
    'setStorageUsersPage(page);',
    'setStorageUsersQuery({',
    '...query,',
    'page: page.page,',
    'size: page.size,',
    'sortBy: page.sortBy,',
    'sortDirection: page.sortDirection,',
  ],
  'cloud console storage user loading must keep backend pagination state',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadTrashNodes(',
    'query: AdminCloudTrashNodesQuery = trashNodesQuery,',
    'if (!authToken || !isAdmin) {',
    'setTrashNodesPage(null);',
    'const page = await fetchAdminCloudOperationTrash(query, authToken);',
  ],
  'cloud console trash operations must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadTrashNodes(',
    'query: AdminCloudTrashNodesQuery = trashNodesQuery,',
    'const page = await fetchAdminCloudOperationTrash(query, authToken);',
    'setTrashNodesPage(page);',
    'setTrashNodesQuery({',
    '...query,',
    'page: page.page,',
    'size: page.size,',
    'sortBy: page.sortBy,',
    'sortDirection: page.sortDirection,',
  ],
  'cloud console trash loading must keep backend pagination state',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadShareLinks(',
    'query: AdminCloudShareLinksQuery = shareLinksQuery,',
    'if (!authToken || !isAdmin) {',
    'setShareLinksPage(null);',
    'const page = await fetchAdminCloudOperationShares(query, authToken);',
  ],
  'cloud console share operations must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadShareLinks(',
    'query: AdminCloudShareLinksQuery = shareLinksQuery,',
    'const page = await fetchAdminCloudOperationShares(query, authToken);',
    'setShareLinksPage(page);',
    'setShareLinksQuery({',
    '...query,',
    'page: page.page,',
    'size: page.size,',
    'sortBy: page.sortBy,',
    'sortDirection: page.sortDirection,',
  ],
  'cloud console share loading must keep backend pagination state',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'async function loadAll(options: DriveOperationsReadOptions = {}) {',
    'loadOverview(options),',
    'loadStorageUsers(storageUsersQuery, options),',
    'loadTrashNodes(trashNodesQuery, options),',
    'loadShareLinks(shareLinksQuery, options),',
  ],
  'cloud console operations refresh must keep overview, storage users, trash, and shares together',
);
assert.match(
  `${consolePageSource}\n${driveOperationsHookSource}`,
  /await operations\.loadAll\(\{ force: true \}\);[\s\S]*onRefresh=\{\(\) => void operations\.loadAll\(\{ force: true \}\)\}[\s\S]*void loadStorageUsers\(nextQuery, \{ force: true \}\);[\s\S]*void loadTrashNodes\(nextQuery, \{ force: true \}\);[\s\S]*void loadShareLinks\(nextQuery, \{ force: true \}\);/,
  'cloud console operations refreshes must force reads after explicit refresh or query changes',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function applyStorageUsersQuery(query: AdminCloudStorageUsersQuery) {',
    'if (storageUsersLoadingRef.current) {',
    'return;',
    'function changeStorageUsersPage(page: number, size: number) {',
    'function applyTrashNodesQuery(query: AdminCloudTrashNodesQuery) {',
    'if (trashNodesLoadingRef.current) {',
    'return;',
    'function changeTrashNodesPage(page: number, size: number) {',
    'function applyShareLinksQuery(query: AdminCloudShareLinksQuery) {',
    'if (shareLinksLoadingRef.current) {',
    'return;',
    'function changeShareLinksPage(page: number, size: number) {',
  ],
  'cloud console operations filters and pagination must pause during loading',
);
assertIncludesInOrder(
  driveOperationsViewSource,
  [
    'const operationsLoading = overviewLoading || storageUsersLoading || trashNodesLoading || shareLinksLoading;',
    'loading={operationsLoading}',
    'disabled={operationsLoading}',
    'disabled={storageUsersLoading}',
    'loading={storageUsersLoading}',
    'disabled={storageUsersLoading}',
    'disabled={trashNodesLoading}',
    'loading={trashNodesLoading}',
    'disabled={trashNodesLoading}',
    'disabled={shareLinksLoading}',
    'loading={shareLinksLoading}',
    'disabled={shareLinksLoading}',
  ],
  'cloud console operations controls must surface pending loading state',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function applyStorageUsersQuery(query: AdminCloudStorageUsersQuery) {',
    '...initialStorageUsersQuery,',
    '...storageUsersQuery,',
    '...query,',
    'page: query.page ?? 1,',
    'size: query.size ?? storageUsersQuery.size ?? DEFAULT_PAGE_SIZE,',
  ],
  'cloud console storage user filters must reset page while preserving page size',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function changeStorageUsersPage(page: number, size: number) {',
    'applyStorageUsersQuery({ ...storageUsersQuery, page, size });',
  ],
  'cloud console storage user pagination must reload with current filters and pagination',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function applyTrashNodesQuery(query: AdminCloudTrashNodesQuery) {',
    '...initialTrashNodesQuery,',
    '...trashNodesQuery,',
    '...query,',
    'page: query.page ?? 1,',
    'size: query.size ?? trashNodesQuery.size ?? DEFAULT_PAGE_SIZE,',
  ],
  'cloud console trash filters must reset page while preserving page size',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function changeTrashNodesPage(page: number, size: number) {',
    'applyTrashNodesQuery({ ...trashNodesQuery, page, size });',
  ],
  'cloud console trash pagination must reload with current filters and pagination',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function applyShareLinksQuery(query: AdminCloudShareLinksQuery) {',
    '...initialShareLinksQuery,',
    '...shareLinksQuery,',
    '...query,',
    'page: query.page ?? 1,',
    'size: query.size ?? shareLinksQuery.size ?? DEFAULT_PAGE_SIZE,',
  ],
  'cloud console share filters must reset page while preserving page size',
);
assertIncludesInOrder(
  driveOperationsHookSource,
  [
    'function changeShareLinksPage(page: number, size: number) {',
    'applyShareLinksQuery({ ...shareLinksQuery, page, size });',
  ],
  'cloud console share pagination must reload with current filters and pagination',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'async function loadAppPackageInfo(options: AppPackageReadOptions = {}) {',
    'if (!authToken || !isAdmin) {',
    'setAppPackageInfo(null);',
    'const nextPackageInfo = await fetchAdminAppPackage(authToken);',
    'setAppPackageInfo(nextPackageInfo);',
  ],
  'cloud console APK admin reads must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'const appPackageLoadingRef = useRef(false);',
    'const publicAppPackageLoadingRef = useRef(false);',
    'const appPackageRequestIdRef = useRef(0);',
    'const appPackageLoadingKeyRef = useRef<string | null>(null);',
    'const publicAppPackageRequestIdRef = useRef(0);',
    'const publicAppPackageLoadingKeyRef = useRef<string | null>(null);',
    'async function loadAppPackageInfo(options: AppPackageReadOptions = {}) {',
    'if (appPackageMutationRef.current !== null) {',
    'if (!options.force && appPackageLoadingKeyRef.current === requestKey) {',
    'appPackageLoadingRef.current = true;',
    'setAppPackageLoading(true);',
    '} finally {',
    'appPackageLoadingKeyRef.current = null;',
    'appPackageLoadingRef.current = false;',
    'setAppPackageLoading(false);',
    'async function loadPublicAppPackageInfo(options: AppPackageReadOptions = {}) {',
    'if (!options.force && publicAppPackageLoadingKeyRef.current === requestKey) {',
    'publicAppPackageLoadingRef.current = true;',
    'setPublicAppPackageLoading(true);',
    '} finally {',
    'publicAppPackageLoadingKeyRef.current = null;',
    'publicAppPackageLoadingRef.current = false;',
    'setPublicAppPackageLoading(false);',
  ],
  'cloud console APK reads must keep synchronous loading guards',
);
assert.match(
  appPackageHookSource,
  /type AppPackageReadOptions = \{[\s\S]*force\?: boolean;[\s\S]*type RefState<T> = \{[\s\S]*current: T;[\s\S]*const authTokenRef = useRef\(authToken\);[\s\S]*const isAdminRef = useRef\(isAdmin\);[\s\S]*const appPackageRequestIdRef = useRef\(0\);[\s\S]*const appPackageLoadingKeyRef = useRef<string \| null>\(null\);[\s\S]*const publicAppPackageRequestIdRef = useRef\(0\);[\s\S]*const publicAppPackageLoadingKeyRef = useRef<string \| null>\(null\);/,
  'cloud console APK reads must track request identity',
);
assert.match(
  appPackageHookSource,
  /function createAppPackageRequestKey\(scope: string, token: string \| null = authToken, admin = isAdmin\) \{[\s\S]*return JSON\.stringify\(\[scope, token, admin\]\);[\s\S]*function isCurrentAppPackageRequest\([\s\S]*const currentToken = scope === 'public' \? null : authTokenRef\.current;[\s\S]*const currentAdmin = scope === 'public' \? true : isAdminRef\.current;[\s\S]*createAppPackageRequestKey\(scope, currentToken, currentAdmin\) === requestKey/,
  'cloud console APK reads must compare auth admin public scope',
);
assert.match(
  appPackageHookSource,
  /async function loadAppPackageInfo\(options: AppPackageReadOptions = \{\}\) \{[\s\S]*if \(appPackageMutationRef\.current !== null\) \{[\s\S]*return;[\s\S]*if \(!options\.force && appPackageLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*const nextPackageInfo = await fetchAdminAppPackage\(authToken\);[\s\S]*if \(!isCurrentAppPackageRequest\(appPackageRequestIdRef, appPackageLoadingKeyRef, requestId, requestKey, 'admin'\)\) \{[\s\S]*return;[\s\S]*setAppPackageInfo\(nextPackageInfo\);[\s\S]*async function loadPublicAppPackageInfo\(options: AppPackageReadOptions = \{\}\) \{[\s\S]*if \(appPackageMutationRef\.current !== null\) \{[\s\S]*return;[\s\S]*if \(!options\.force && publicAppPackageLoadingKeyRef\.current === requestKey\) \{[\s\S]*return;[\s\S]*const nextPackageInfo = await fetchPublicAppPackage\(\);[\s\S]*!isCurrentAppPackageRequest\([\s\S]*'public'[\s\S]*setPublicAppPackageInfo\(nextPackageInfo\);/,
  'cloud console APK reads must block duplicate reads and ignore stale responses',
);
assert.match(
  appPackageHookSource,
  /function cancelAppPackageReads\(\) \{[\s\S]*appPackageRequestIdRef\.current \+= 1;[\s\S]*appPackageLoadingKeyRef\.current = null;[\s\S]*publicAppPackageRequestIdRef\.current \+= 1;[\s\S]*publicAppPackageLoadingKeyRef\.current = null;[\s\S]*cancelAppPackageReads\(\);[\s\S]*appPackageMutationRef\.current = 'upload';[\s\S]*cancelAppPackageReads\(\);[\s\S]*appPackageMutationRef\.current = 'delete';/,
  'cloud console APK mutations must invalidate stale package reads',
);
assert.match(
  appPackageHookSource,
  /useEffect\(\(\) => \{[\s\S]*appPackageRequestIdRef\.current \+= 1;[\s\S]*appPackageLoadingKeyRef\.current = null;[\s\S]*appPackageLoadingRef\.current = false;[\s\S]*setAppPackageLoading\(false\);[\s\S]*setAppPackageInfo\(null\);[\s\S]*\}, \[authToken, isAdmin\]\);/,
  'cloud console APK admin reads must invalidate auth admin scope changes',
);
assert.match(
  `${consolePageSource}\n${appPackageHookSource}`,
  /const appPackageBusy =[\s\S]*appPackages\.publicAppPackageLoading[\s\S]*await appPackages\.loadAppPackageState\(\{ force: true \}\);[\s\S]*async function loadAppPackageState\(options: AppPackageReadOptions = \{\}\) \{[\s\S]*loadAppPackageInfo\(options\),[\s\S]*loadPublicAppPackageInfo\(options\),[\s\S]*void loadAppPackageState\(\);/,
  'cloud console APK refreshes must force paired admin and public package reads',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'function openAppPackageUploadModal() {',
    'if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {',
    'return;',
    'setAppPackageUploadOpen(true);',
  ],
  'cloud console APK upload modal must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'function handleAppPackageFileChange(event: ChangeEvent<HTMLInputElement>)',
    'if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {',
    "event.target.value = '';",
    'return;',
  ],
  'cloud console APK file selection must stay behind the cloud admin gate',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    "const appPackageMutationRef = useRef<'upload' | 'delete' | null>(null);",
    'async function submitAppPackageUpload(values: AppPackageUploadFormValues)',
    'if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {',
    "const requestToken = authToken;",
    "const requestKey = createAppPackageMutationRequestKey('upload', requestToken, isAdmin);",
    "appPackageMutationRef.current = 'upload';",
    'setAppPackageUploading(true);',
    'const nextPackageInfo = await uploadAdminAppPackage(',
    '} finally {',
    'appPackageMutationRef.current = null;',
    'setAppPackageUploading(false);',
  ],
  'cloud console APK upload mutations must stay behind the cloud admin gate and block duplicate submissions',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'const [appPackageDeleting, setAppPackageDeleting] = useState(false);',
    "const appPackageMutationRef = useRef<'upload' | 'delete' | null>(null);",
    'async function deleteCurrentAppPackage()',
    'if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {',
    "const requestToken = authToken;",
    "const requestKey = createAppPackageMutationRequestKey('delete', requestToken, isAdmin);",
    "appPackageMutationRef.current = 'delete';",
    'setAppPackageDeleting(true);',
    'await deleteAdminAppPackage(requestToken);',
    '} finally {',
    'appPackageMutationRef.current = null;',
    'setAppPackageDeleting(false);',
  ],
  'cloud console APK delete mutations must stay behind the cloud admin gate and block duplicate submissions',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'function createAppPackageMutationRequestKey(',
    'return JSON.stringify([scope, token, admin]);',
    "function isCurrentAppPackageMutationRequest(requestKey: string, scope: 'upload' | 'delete')",
    'appPackageMutationRef.current === scope',
    'createAppPackageMutationRequestKey(scope, authTokenRef.current, isAdminRef.current) === requestKey',
    'async function submitAppPackageUpload(values: AppPackageUploadFormValues)',
    "const requestKey = createAppPackageMutationRequestKey('upload', requestToken, isAdmin);",
    'const nextPackageInfo = await uploadAdminAppPackage(',
    "if (!isCurrentAppPackageMutationRequest(requestKey, 'upload')) {",
    "message.success('APK、版本号和更新说明已同步更新。');",
    "if (isCurrentAppPackageMutationRequest(requestKey, 'upload')) {",
    'async function deleteCurrentAppPackage()',
    "const requestKey = createAppPackageMutationRequestKey('delete', requestToken, isAdmin);",
    'await deleteAdminAppPackage(requestToken);',
    "if (!isCurrentAppPackageMutationRequest(requestKey, 'delete')) {",
    "message.success('当前安装包已移除。');",
    "if (isCurrentAppPackageMutationRequest(requestKey, 'delete')) {",
  ],
  'cloud console APK mutations must ignore stale auth admin scope',
);
assertIncludesInOrder(
  appPackageHookSource,
  [
    'function closeAppPackageUploadModal() {',
    'if (appPackageMutationRef.current !== null) {',
    'function handleAppPackageFilePickerClick() {',
    'if (appPackageLoadingRef.current || appPackageMutationRef.current !== null) {',
  ],
  'cloud console APK upload modal controls must pause during package mutations',
);
assertIncludesInOrder(
  appPackageUploadModalSource,
  [
    'confirmLoading={uploading}',
    'maskClosable={!uploading}',
    'closable={!uploading}',
    'cancelButtonProps={{ disabled: uploading }}',
    'disabled={uploading}',
    'disabled={uploading}',
  ],
  'cloud console APK upload modal must surface pending upload state',
);
assertIncludesInOrder(
  appPackagePanelSource,
  [
    'deleting,',
    'const packageBusy = loading || uploading || deleting;',
    'disabled={loading || deleting}',
    'okButtonProps={{ danger: true, loading: deleting, disabled: packageBusy }}',
    'cancelButtonProps={{ disabled: loading || deleting }}',
    'loading={deleting}',
    'disabled={packageBusy}',
  ],
  'cloud console APK package panel must surface pending delete state',
);
assertIncludesInOrder(
  appPackagePanelSource,
  [
    'deleting,',
    'const packageBusy = loading || uploading || deleting;',
    'disabled={loading || deleting}',
    'disabled={packageBusy}',
  ],
  'cloud console APK package panel must pause package actions during loading',
);
assertIncludesInOrder(
  consolePageSource,
  [
    'const appPackageBusy =',
    'appPackages.appPackageLoading ||',
    'appPackages.publicAppPackageLoading ||',
    'appPackages.appPackageUploading ||',
    'appPackages.appPackageDeleting;',
    'deleting={appPackages.appPackageDeleting}',
    'onDeletePackage={appPackages.deleteCurrentAppPackage}',
    'loading={activeViewLoading}',
  ],
  'cloud console APK package view must wire pending delete state through the page',
);
assert.match(
  consolePageSource,
  /subTitle="仅全局管理员或云盘管理员可以访问运营后台。"/,
  'cloud console permission denied copy must mention global admins and cloud admins',
);
assert.match(
  cloudUsersViewSource,
  /description="仅全局管理员或云盘管理员可以查看用户画像和调整存储额度。"/,
  'cloud users view permission copy must mention global admins and cloud admins',
);
assert.match(
  cloudUsersViewSource,
  /cloudRoleLabel\(user\)/,
  'cloud users view role tags must use the centralized cloud role label copy',
);
assert.match(
  driveOperationsViewSource,
  /description="仅全局管理员或云盘管理员可以查看全局文件运营明细。"/,
  'cloud operations view permission copy must mention global admins and cloud admins',
);
assert.match(
  driveOperationsViewSource,
  /cloudRoleLabel\(user\)/,
  'cloud operations storage users role tags must use the centralized cloud role label copy',
);
assert.match(
  driveAppPackageViewSource,
  /description="仅全局管理员或云盘管理员可以上传和替换安卓安装包。"/,
  'cloud APK package view permission copy must mention global admins and cloud admins',
);
assert.match(consolePageSource, /key:\s*'consoleHome'/, 'cloud console account menu must expose the unified console gateway');
assert.match(consolePageSource, /key:\s*'profile'/, 'cloud console account menu must expose current user profile editing');
assert.match(
  consolePageSource,
  /title=\{<AliciaModalTitle eyebrow="Account">个人资料<\/AliciaModalTitle>\}[\s\S]*rootClassName="alicia-modal alicia-account-modal account-profile-modal"[\s\S]*className="account-profile-form"[\s\S]*className="profile-avatar-row account-profile-hero"[\s\S]*<strong>用户图标<\/strong>[\s\S]*上传本地图片或填写图片地址后，会同步更新所有 Alicia 账号入口。[\s\S]*className="account-profile-actions"[\s\S]*name="nickname"[\s\S]*name="phoneNumber"[\s\S]*name="avatarUrl"/,
  'cloud console profile modal must keep the shared profile dialog contract',
);
assert.doesNotMatch(
  consolePageSource,
  /profile-avatar-preview-row|profile-avatar-actions/,
  'cloud console profile modal must not keep legacy profile layout aliases',
);
assert.match(consolePageSource, /className="account-profile-form"/, 'cloud console profile modal must use the unified account profile form layout');
assert.match(consolePageSource, /name="avatarUrl"/, 'cloud console profile modal must keep the shared avatar URL field');
assertIncludesInOrder(
  consolePageSource,
  [
    'const [profileSaving, setProfileSaving] = useState(false);',
    'const [avatarUploading, setAvatarUploading] = useState(false);',
    'const profileSavingRef = useRef(false);',
    'const avatarUploadingRef = useRef(false);',
    'function closeProfileModal() {',
    'if (profileSavingRef.current || avatarUploadingRef.current) {',
    'async function submitProfile(values: UpdateProfilePayload) {',
    'if (!authToken || profileSavingRef.current) {',
    'profileSavingRef.current = true;',
    'setProfileSaving(true);',
    'await updateProfile(',
    '} finally {',
    'profileSavingRef.current = false;',
    'setProfileSaving(false);',
  ],
  'cloud console profile updates must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  consolePageSource,
  [
    'function handleAvatarButtonClick() {',
    'if (profileSavingRef.current || avatarUploadingRef.current) {',
    'async function handleAvatarFileChange(event: ChangeEvent<HTMLInputElement>)',
    'if (!authToken || profileSavingRef.current || avatarUploadingRef.current) {',
    'avatarUploadingRef.current = true;',
    'setAvatarUploading(true);',
    'await uploadCurrentUserAvatar(selectedFile, requestToken);',
    '} finally {',
    'avatarUploadingRef.current = false;',
    'setAvatarUploading(false);',
  ],
  'cloud console profile avatar upload must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  consolePageSource,
  [
    'const authTokenRef = useRef(authToken);',
    'authTokenRef.current = authToken;',
    'function isCurrentProfileMutation(token: string) {',
    'return authTokenRef.current === token;',
    'async function handleAvatarFileChange(event: ChangeEvent<HTMLInputElement>)',
    'const requestToken = authToken;',
    'await uploadCurrentUserAvatar(selectedFile, requestToken);',
    'if (!isCurrentProfileMutation(requestToken)) {',
    'updateCurrentUser(updatedUser);',
    'if (isCurrentProfileMutation(requestToken)) {',
    'async function submitProfile(values: UpdateProfilePayload) {',
    'const requestToken = authToken;',
    'await updateProfile(',
    'requestToken,',
    'if (!isCurrentProfileMutation(requestToken)) {',
    'updateCurrentUser(updatedUser);',
    'if (isCurrentProfileMutation(requestToken)) {',
  ],
  'cloud console profile mutations must ignore stale auth scope',
);
assertIncludesInOrder(
  consolePageSource,
  [
    'confirmLoading={profileSaving}',
    'maskClosable={!profileSaving && !avatarUploading}',
    'closable={!profileSaving && !avatarUploading}',
    'cancelButtonProps={{ disabled: profileSaving || avatarUploading }}',
    'disabled={profileSaving}',
    'loading={avatarUploading}',
    'disabled={profileSaving}',
  ],
  'cloud console profile modal must surface pending profile updates',
);
assert.match(consoleStyles, /\.account-profile-form/, 'cloud console styles must define the unified account profile form');
assert.match(consoleStyles, /\.account-profile-hero/, 'cloud console styles must define the unified profile avatar function area');
assert.match(consoleStyles, /\.account-profile-copy/, 'cloud console styles must define the unified profile explanatory copy');
assert.match(consoleStyles, /\.account-profile-actions/, 'cloud console styles must define the unified profile action row');
assert.match(consoleStyles, /\.account-profile-fields/, 'cloud console styles must define the unified profile field stack');
assert.doesNotMatch(
  consoleStyles,
  /\.profile-avatar-preview-row\b|\.profile-avatar-actions\b/,
  'cloud console styles must not keep legacy profile layout aliases',
);
assert.match(
  cloudUsersViewSource,
  /title=\{<AliciaModalTitle eyebrow="Cloud">调整云盘额度<\/AliciaModalTitle>\}/,
  'cloud quota modal must use the Alicia modal title',
);
assert.match(
  cloudUsersViewSource,
  /rootClassName="alicia-modal alicia-account-modal cloud-quota-modal"/,
  'cloud quota modal must use the unified Alicia modal chrome',
);
assert.match(
  consolePageSource,
  /window\.location\.assign\('\/console\/'\)/,
  'cloud console account menu must route management navigation through /console/',
);
assert.doesNotMatch(
  consolePageSource,
  /window\.location\.assign\('\/console\/identity\/?'\)|href="\/console\/identity(?:\/|")/,
  'cloud console should use the unified console gateway instead of hard-linking to the identity console',
);
assert.match(
  consolePageSource,
  /title="没有云盘后台权限"[\s\S]*href="\/console\/"[\s\S]*href="\/cloudPan\/"[\s\S]*href="\/"/,
  'cloud console permission denied state must expose routes back to the console gateway, cloud web, and main site',
);

console.log('[OK] cloud console boundary verified');
