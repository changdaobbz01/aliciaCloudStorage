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
  { pattern: /\bAdminCloud[A-Za-z0-9_]*\b/, reason: 'admin cloud types belong in sysManage' },
  { pattern: /\bIdentityAudit[A-Za-z0-9_]*\b/, reason: 'identity audit types belong in mainSite/userSite' },
  { pattern: /\b(fetchUsers|createUser|updateUserStorageQuota|resetUserPassword)\b/, reason: 'user administration belongs outside webApp' },
  { pattern: /\b(fetchAdminCloud|fetchAdminAppPackage|uploadAdminAppPackage|deleteAdminAppPackage)\b/, reason: 'cloud administration belongs in sysManage' },
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

const files = listSourceFiles(srcRoot);

for (const fileUrl of files) {
  const relativePath = relative(srcRootPath, fileURLToPath(fileUrl)).replaceAll('\\', '/');
  const source = readFileSync(fileUrl, 'utf8');

  for (const { pattern, reason } of forbiddenPatterns) {
    assert.doesNotMatch(source, pattern, `${relativePath} must not contain ${pattern}: ${reason}`);
  }
}

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

console.log('[OK] cloud web client boundary verified');
