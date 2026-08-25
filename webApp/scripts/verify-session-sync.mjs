import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const session = readFileSync(new URL('../src/lib/session.ts', import.meta.url), 'utf8');
const sessionContext = readFileSync(new URL('../src/context/session-context.tsx', import.meta.url), 'utf8');

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

for (const reason of ['profile', 'logout']) {
  assert.match(
    sessionContext,
    new RegExp(`notifySessionChanged\\('${reason}'\\)`),
    `SessionProvider must notify ${reason} changes`,
  );
}

console.log('[OK] cloud web browser session sync boundary verified');
