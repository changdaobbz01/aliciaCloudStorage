import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const session = readFileSync(new URL('../src/lib/session.ts', import.meta.url), 'utf8');
const sessionContext = readFileSync(new URL('../src/context/session-context.tsx', import.meta.url), 'utf8');
const protectedRoute = readFileSync(new URL('../src/components/protected-route.tsx', import.meta.url), 'utf8');
const unifiedLogin = readFileSync(new URL('../src/lib/unifiedLogin.ts', import.meta.url), 'utf8');

assert.match(
  session,
  /SESSION_REVISION_STORAGE_KEY = 'alicia-cloud-storage\.session-revision'/,
  'cloud console must keep the shared session revision key',
);
assert.match(
  session,
  /SESSION_WRITE_LOCK_STORAGE_KEY = 'alicia-cloud-storage\.session-write-lock'/,
  'cloud console must keep a shared session write lock key',
);
assert.match(
  session,
  /SESSION_CHANGE_EVENT = 'alicia-cloud-storage:session-change'/,
  'cloud console must keep the same-document session change event',
);
assert.match(
  session,
  /SESSION_STORAGE_KEYS[\s\S]*SESSION_REVISION_STORAGE_KEY/,
  'session revision key must be part of the watched session storage keys',
);
assert.match(session, /function notifySessionChanged/, 'cloud console session utility must expose session change notifications');
assert.match(
  session,
  /function runAfterSessionWriteSettled/,
  'cloud console session utility must defer session listeners during multi-key writes',
);
assert.match(
  session,
  /function readStoredSessionSnapshot/,
  'cloud console session utility must snapshot stored tokens before refresh',
);
assert.match(
  session,
  /function hasStoredSessionChanged/,
  'cloud console session utility must detect token changes during refresh',
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

for (const reason of ['profile', 'logout', 'expired']) {
  assert.match(
    sessionContext,
    new RegExp(`notifySessionChanged\\('${reason}'\\)`),
    `SessionProvider must notify ${reason} changes`,
  );
}

console.log('[OK] cloud console browser session sync boundary verified');
