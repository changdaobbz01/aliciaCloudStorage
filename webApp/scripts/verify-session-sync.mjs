import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const session = readFileSync(new URL('../src/lib/session.ts', import.meta.url), 'utf8');
const sessionContext = readFileSync(new URL('../src/context/session-context.tsx', import.meta.url), 'utf8');
const protectedRoute = readFileSync(new URL('../src/components/protected-route.tsx', import.meta.url), 'utf8');
const unifiedLogin = readFileSync(new URL('../src/lib/unifiedLogin.ts', import.meta.url), 'utf8');

assert.match(
  session,
  /SESSION_REVISION_STORAGE_KEY = 'alicia-cloud-storage\.session-revision'/,
  'cloud web must keep the shared session revision key',
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

for (const token of ['SESSION_CHANGE_EVENT', 'SESSION_REVISION_STORAGE_KEY', 'isSessionRevisionStorageKey']) {
  assert.match(sessionContext, new RegExp(token), `SessionProvider must use ${token}`);
}

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

console.log('[OK] cloud web browser session sync boundary verified');
