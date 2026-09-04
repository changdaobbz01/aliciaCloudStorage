import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const session = readFileSync(new URL('../src/lib/session.ts', import.meta.url), 'utf8');
const api = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8');
const sessionContext = readFileSync(new URL('../src/context/session-context.tsx', import.meta.url), 'utf8');
const protectedRoute = readFileSync(new URL('../src/components/protected-route.tsx', import.meta.url), 'utf8');
const unifiedLogin = readFileSync(new URL('../src/lib/unifiedLogin.ts', import.meta.url), 'utf8');
const driveProfileSettings = readFileSync(new URL('../src/features/drive/hooks/useDriveProfileSettings.ts', import.meta.url), 'utf8');
const driveProfileModals = readFileSync(new URL('../src/features/drive/DriveProfileModals.tsx', import.meta.url), 'utf8');
const drivePage = readFileSync(new URL('../src/pages/DrivePage.tsx', import.meta.url), 'utf8');
const statusPanel = readFileSync(new URL('../src/components/StatusPanel.tsx', import.meta.url), 'utf8');

function findMatching(source, openIndex, openChar, closeChar) {
  let depth = 0;

  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === openChar) {
      depth += 1;
    } else if (source[index] === closeChar) {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  throw new Error(`No matching ${closeChar} found.`);
}

function extractFunctionSource(source, functionName) {
  const marker = `function ${functionName}(`;
  const startIndex = source.indexOf(marker);
  assert.ok(startIndex > -1, `${functionName} must exist`);

  const openIndex = source.indexOf('{', startIndex);
  assert.ok(openIndex > -1, `${functionName} must use a function body`);

  const closeIndex = findMatching(source, openIndex, '{', '}');
  return source.slice(openIndex + 1, closeIndex);
}

function assertLocalLogoutFlow(source, functionName, label) {
  const functionSource = extractFunctionSource(source, functionName);

  assert.match(
    functionSource,
    /await logoutAuthToken\(token,\s*refreshToken\)[\s\S]*catch\s*\{/,
    `${label} must ignore server logout failures before local logout`,
  );
  assert.match(
    functionSource,
    /resetSessionState\(\);[\s\S]*notifySessionChanged\('logout'\)/,
    `${label} must clear the local session before notifying logout`,
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

assert.match(
  session,
  /SESSION_REVISION_STORAGE_KEY = 'alicia-cloud-storage\.session-revision'/,
  'cloud web must keep the shared session revision key',
);
assert.match(
  session,
  /SESSION_WRITE_LOCK_STORAGE_KEY = 'alicia-cloud-storage\.session-write-lock'/,
  'cloud web must keep a shared session write lock key',
);
assert.match(
  session,
  /SESSION_CHANGE_EVENT = 'alicia-cloud-storage:session-change'/,
  'cloud web must keep the same-document session change event',
);
assert.match(
  session,
  /SESSION_STORAGE_KEYS[\s\S]*SESSION_REVISION_STORAGE_KEY/,
  'session revision key must be part of the watched session storage keys',
);
assert.match(session, /function notifySessionChanged/, 'cloud session utility must expose session change notifications');
assert.match(session, /function sanitizeStoredSession/, 'cloud session utility must sanitize legacy browser session residue');
assert.match(session, /sanitizeStoredSession\(\);/, 'cloud session utility must run legacy browser session sanitation on startup');
assert.match(
  session,
  /if \(\(!token \|\| !refreshToken\) && !isSessionWriteLocked\(\)\) \{\s*clearCurrentSession\(\);\s*\}/,
  'cloud session utility must clear partial stored token pairs once no session write is active',
);
assert.match(
  session,
  /function runAfterSessionWriteSettled/,
  'cloud session utility must defer session listeners during multi-key writes',
);
assert.match(
  session,
  /function readStoredSessionSnapshot/,
  'cloud session utility must snapshot stored tokens before refresh',
);
assert.match(
  session,
  /function hasStoredSessionChanged/,
  'cloud session utility must detect token changes during refresh',
);
assert.match(api, /import \{ loadAuthToken \} from '\.\/session'/, 'cloud API helper must compare 401 responses with the current stored token');
assert.match(api, /function shouldDispatchAuthExpiredForToken/, 'cloud API helper must ignore stale-token 401 responses');
assert.match(api, /xhr\.status === 401 && shouldDispatchAuthExpiredForToken\(token\)/, 'cloud upload API helper must ignore stale-token 401 responses');
assert.match(
  api,
  /refreshAuthSession[\s\S]*dispatchAuthExpired: false/,
  'cloud refresh requests must not broadcast global session expiry before snapshot checks',
);
assert.match(
  api,
  /logoutAuthToken[\s\S]*dispatchAuthExpired: false/,
  'cloud logout requests must not broadcast global session expiry after local logout starts',
);

for (const token of [
  'SESSION_CHANGE_EVENT',
  'SESSION_REVISION_STORAGE_KEY',
  'runAfterSessionWriteSettled',
  'isSessionRevisionStorageKey',
  'isSessionWriteLocked',
  'readStoredSessionSnapshot',
  'hasStoredSessionChanged',
]) {
  assert.match(sessionContext, new RegExp(token), `SessionProvider must use ${token}`);
}

assert.match(
  sessionContext,
  /hasStoredSessionChanged\(snapshot\)/,
  'SessionProvider must not clear or overwrite a newer session after stale refresh',
);
assert.match(sessionContext, /function isAuthenticationSessionError/, 'SessionProvider must distinguish authentication failures from transient API failures');
assert.match(
  sessionContext,
  /isAuthenticationSessionError\(error\)/,
  'SessionProvider must only expire local sessions on authentication failures',
);
assert.match(
  sessionContext,
  /function confirmCurrentSessionExpired/,
  'SessionProvider must confirm the current session before redirecting on auth-expired events',
);
assert.match(
  sessionContext,
  /hasStoredSessionChanged\(snapshot\)/,
  'SessionProvider must ignore auth-expired events when another page has already written a newer session',
);
assert.match(
  sessionContext,
  /const refreshedSession = await refreshAuthSession\(token, refreshToken\)/,
  'SessionProvider must try to refresh the current session before clearing it after auth-expired events',
);
assert.match(
  sessionContext,
  /if \(!token && !refreshToken\) \{\s*resetSessionState\(\);\s*return;\s*\}/,
  'SessionProvider must treat auth-expired events with no stored session as logout/no-op rather than session expiry',
);
assert.match(sessionContext, /const cachedUser = loadCurrentUser\(\)/, 'SessionProvider must use cached user snapshots while restoring sessions');
assert.match(sessionContext, /function toCachedCloudUser/, 'SessionProvider must derive a cloud-safe cached user from identity login sessions');
assert.match(sessionContext, /saveCurrentUser\(toCachedCloudUser\(refreshedSession\.user\)\)/, 'SessionProvider token refresh must seed the cached current user snapshot');
assert.match(
  sessionContext,
  /window\.addEventListener\('pageshow', handlePageShow\)/,
  'SessionProvider must restore stored sessions after browser history cache restores',
);
assert.match(
  sessionContext,
  /window\.removeEventListener\('pageshow', handlePageShow\)/,
  'SessionProvider must clean up the history cache restore listener',
);

assert.match(
  unifiedLogin,
  /reason=session-expired|searchParams\.set\('reason'/,
  'unified login redirect must preserve a safe redirect reason',
);
assert.match(
  sessionContext,
  /loginRedirectReason/,
  'SessionProvider must keep the login redirect reason after session expiry',
);
assert.match(
  protectedRoute,
  /loginRedirectReason/,
  'ProtectedRoute must pass the session expiry reason to unified login',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'async function loadIdentitySessions(nextIncludeRevoked = includeRevokedSessions, options: IdentitySessionsLoadOptions = {}) {',
    'if (!authToken) {',
    'const requestKey = createIdentitySessionsRequestKey(authToken, nextIncludeRevoked);',
    'setIdentitySessionsLoading(true);',
    'const nextSessions = await fetchIdentitySessions(authToken, nextIncludeRevoked);',
    'setIdentitySessions(nextSessions);',
  ],
  'cloud web profile sessions must load current identity sessions with the selected revoked filter',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'function changeIncludeRevokedSessions(checked: boolean) {',
    'includeRevokedSessionsRef.current = checked;',
    'setIncludeRevokedSessions(checked);',
    'void loadIdentitySessions(checked, { force: true });',
  ],
  'cloud web profile sessions include-revoked toggle must reload with the selected state',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'async function revokeSession(sessionId: number) {',
    'if (!authToken) {',
    'if (identitySessionRevokingIdRef.current !== null) {',
    'identitySessionRevokingIdRef.current = sessionId;',
    'setIdentitySessionRevokingId(sessionId);',
    'await revokeIdentitySession(authToken, sessionId);',
    'message.success',
    'await loadIdentitySessions(includeRevokedSessionsRef.current, { force: true });',
    '} finally {',
    'identitySessionRevokingIdRef.current = null;',
    'setIdentitySessionRevokingId(null);',
  ],
  'cloud web profile session revocation must preserve the current revoked filter and block duplicate submissions',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'const [identitySessionRevokingId, setIdentitySessionRevokingId] = useState<number | null>(null);',
    'const identitySessionRevokingIdRef = useRef<number | null>(null);',
  ],
  'cloud web profile session revocation must track pending submissions',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'function closeSessionsModal() {',
    'if (identitySessionsLoadingRef.current || identitySessionRevokingIdRef.current !== null) {',
    'setSessionsOpen(false);',
  ],
  'cloud web profile session modal close must pause during session revocation',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'function changeIncludeRevokedSessions(checked: boolean) {',
    'if (identitySessionsLoadingRef.current || identitySessionRevokingIdRef.current !== null) {',
    'setIncludeRevokedSessions(checked);',
    'void loadIdentitySessions(checked, { force: true });',
  ],
  'cloud web profile session filter must pause during session revocation',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'async function refreshIdentitySessions() {',
    'if (identitySessionsLoadingRef.current || identitySessionRevokingIdRef.current !== null) {',
    'await loadIdentitySessions(includeRevokedSessionsRef.current, { force: true });',
  ],
  'cloud web profile session refresh must pause during session revocation',
);
assertIncludesInOrder(
  drivePage,
  [
    'onRefreshSessions={profileSettings.refreshIdentitySessions}',
  ],
  'cloud web drive page must route session refresh through the guarded profile action',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'const [profileSaving, setProfileSaving] = useState(false);',
    'const profileSavingRef = useRef(false);',
    'const avatarUploadingRef = useRef(false);',
    'const backgroundUploadingRef = useRef(false);',
    'const backgroundClearingRef = useRef(false);',
    'function closeProfileModal() {',
    'profileSavingRef.current ||',
    'avatarUploadingRef.current ||',
    'backgroundUploadingRef.current ||',
    'backgroundClearingRef.current',
    'async function submitProfile(values: UpdateProfilePayload)',
    'if (!authToken || profileSavingRef.current) {',
    'profileSavingRef.current = true;',
    'setProfileSaving(true);',
    'await updateProfile(',
    '} finally {',
    'profileSavingRef.current = false;',
    'setProfileSaving(false);',
  ],
  'cloud web profile updates must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'const avatarUploadingRef = useRef(false);',
    'function handleAvatarButtonClick() {',
    'if (profileSavingRef.current || avatarUploadingRef.current) {',
    'async function handleAvatarFileChange(event: ChangeEvent<HTMLInputElement>)',
    'if (!authToken || profileSavingRef.current || avatarUploadingRef.current) {',
    "event.target.value = '';",
    'avatarUploadingRef.current = true;',
    'setAvatarUploading(true);',
    'await uploadCurrentUserAvatar(selectedFile, authToken);',
    '} finally {',
    'avatarUploadingRef.current = false;',
    'setAvatarUploading(false);',
  ],
  'cloud web avatar upload must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  driveProfileModals,
  [
    'confirmLoading={profileSaving}',
    'maskClosable={!profileSaving && !avatarUploading}',
    'closable={!profileSaving && !avatarUploading}',
    'cancelButtonProps={{ disabled: profileSaving || avatarUploading }}',
    'disabled={profileSaving}',
  ],
  'cloud web profile modal must surface pending profile updates',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'const [backgroundUploading, setBackgroundUploading] = useState(false);',
    'const [backgroundClearing, setBackgroundClearing] = useState(false);',
    'const backgroundUploadingRef = useRef(false);',
    'const backgroundClearingRef = useRef(false);',
    'function handleHomeBackgroundButtonClick() {',
    'if (backgroundUploadingRef.current || backgroundClearingRef.current) {',
    'async function handleHomeBackgroundFileChange(event: ChangeEvent<HTMLInputElement>)',
    'if (!authToken || backgroundUploadingRef.current || backgroundClearingRef.current) {',
    "event.target.value = '';",
    'backgroundUploadingRef.current = true;',
    'setBackgroundUploading(true);',
    'await uploadCurrentUserHomeBackground(selectedFile, authToken);',
    '} finally {',
    'backgroundUploadingRef.current = false;',
    'setBackgroundUploading(false);',
    'async function clearHomeBackground()',
    'if (!authToken || backgroundUploadingRef.current || backgroundClearingRef.current) {',
    'backgroundClearingRef.current = true;',
    'setBackgroundClearing(true);',
    'await clearCurrentUserHomeBackground(authToken);',
    '} finally {',
    'backgroundClearingRef.current = false;',
    'setBackgroundClearing(false);',
  ],
  'cloud web home background mutations must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'const [passwordSaving, setPasswordSaving] = useState(false);',
    'const passwordSavingRef = useRef(false);',
    'function closePasswordModal() {',
    'if (passwordSavingRef.current) {',
    'async function submitPassword(values: PasswordFormValues)',
    'if (!authToken || passwordSavingRef.current) {',
    'passwordSavingRef.current = true;',
    'setPasswordSaving(true);',
    'await changePassword(',
    "message.error(passwordError instanceof Error ? passwordError.message : '密码修改失败。');",
    'passwordSavingRef.current = false;',
    'setPasswordSaving(false);',
  ],
  'cloud web password change must block duplicate submissions and report failures',
);
assertIncludesInOrder(
  driveProfileModals,
  [
    'passwordSaving,',
    'confirmLoading={passwordSaving}',
    'maskClosable={!passwordSaving}',
    'closable={!passwordSaving}',
    'cancelButtonProps={{ disabled: passwordSaving }}',
    'disabled={passwordSaving}',
  ],
  'cloud web password modal must surface pending password changes',
);
assertIncludesInOrder(
  driveProfileModals,
  [
    'okButtonProps={{',
    'loading: revokingThisSession,',
    'disabled: identitySessionRevokingId !== null && !revokingThisSession,',
    'cancelButtonProps={{ disabled: revokingThisSession }}',
    'loading={revokingThisSession}',
    'disabled={identitySessionRevokingId !== null && !revokingThisSession}',
    'maskClosable={!identitySessionsLoading && identitySessionRevokingId === null}',
    'closable={!identitySessionsLoading && identitySessionRevokingId === null}',
    'disabled={identitySessionsLoading || identitySessionRevokingId !== null}',
  ],
  'cloud web session modal must surface pending session revocation',
);
assertIncludesInOrder(
  driveProfileModals,
  [
    'const revokingThisSession = identitySessionRevokingId === session.id;',
    'loading: revokingThisSession,',
    'disabled: identitySessionRevokingId !== null && !revokingThisSession,',
    'disabled={identitySessionRevokingId !== null && !revokingThisSession}',
  ],
  'cloud web session modal must disable competing rows during session revocation',
);
assertIncludesInOrder(
  statusPanel,
  [
    'backgroundUploading,',
    'backgroundClearing,',
    'loading={backgroundUploading}',
    'disabled={backgroundClearing}',
    'loading={backgroundClearing}',
    'disabled={backgroundUploading || backgroundClearing}',
  ],
  'cloud web home background controls must surface pending state',
);
assertIncludesInOrder(
  drivePage,
  [
    'backgroundUploading={profileSettings.backgroundUploading}',
    'backgroundClearing={profileSettings.backgroundClearing}',
    'disabled={profileSettings.backgroundUploading || profileSettings.backgroundClearing}',
    'passwordSaving={profileSettings.passwordSaving}',
  ],
  'cloud web drive page must wire profile pending states to the UI',
);
assertIncludesInOrder(
  sessionContext,
  [
    'isLoggingOut: boolean;',
    'const logoutSubmittingRef = useRef(false);',
    'const [isLoggingOut, setIsLoggingOut] = useState(false);',
    'async function logoutCurrentSession() {',
    'if (logoutSubmittingRef.current) {',
    'logoutSubmittingRef.current = true;',
    'setIsLoggingOut(true);',
    'await logoutAuthToken(token, refreshToken);',
    '} finally {',
    'resetSessionState();',
    "notifySessionChanged('logout');",
    'logoutSubmittingRef.current = false;',
    'setIsLoggingOut(false);',
    'isLoggingOut,',
  ],
  'cloud web logout must block duplicate submissions and surface pending state',
);
assertIncludesInOrder(
  driveProfileSettings,
  [
    'isLoggingOut: boolean;',
    'isLoggingOut,',
    'const logoutNavigatingRef = useRef(false);',
    'async function handleLogout() {',
    'if (isLoggingOut || logoutNavigatingRef.current) {',
    'logoutNavigatingRef.current = true;',
    'await logoutCurrentSession();',
    'onNavigateToLogin();',
    'logoutNavigatingRef.current = false;',
    'isLoggingOut,',
  ],
  'cloud web profile menu logout must block duplicate submissions',
);
assertIncludesInOrder(
  drivePage,
  [
    'const { authToken, currentUser, clearCurrentSession, isLoggingOut, logoutCurrentSession, updateCurrentUser } = useSession();',
    'isLoggingOut,',
    "{ key: 'logout', icon: <Icon icon={LogOut} />, label: isLoggingOut ? '退出中' : '退出登录', danger: true, disabled: isLoggingOut },",
    '<Dropdown',
    'disabled={isLoggingOut}',
    '<button type="button" className="avatar-menu-button" aria-label="打开用户菜单" disabled={isLoggingOut}>',
  ],
  'cloud web account menu must surface pending logout state',
);

for (const reason of ['profile', 'logout', 'expired']) {
  assert.match(
    sessionContext,
    new RegExp(`notifySessionChanged\\('${reason}'\\)`),
    `SessionProvider must notify ${reason} changes`,
  );
}

assertLocalLogoutFlow(sessionContext, 'logoutCurrentSession', 'cloud web logout');

console.log('[OK] cloud web browser session sync boundary verified');
