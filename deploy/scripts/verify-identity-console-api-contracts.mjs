import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));

function parseArgs(argv) {
  const args = {
    cloudProjectDir: resolve(scriptDir, '../..'),
    mainSiteProjectDir: process.env.ALICIA_MAIN_SITE_PROJECT_DIR
      ? resolve(process.env.ALICIA_MAIN_SITE_PROJECT_DIR)
      : null,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];

    if (value === '--cloud') {
      args.cloudProjectDir = resolve(argv[index + 1] ?? '');
      index += 1;
    } else if (value === '--main-site') {
      args.mainSiteProjectDir = resolve(argv[index + 1] ?? '');
      index += 1;
    }
  }

  if (!args.mainSiteProjectDir) {
    args.mainSiteProjectDir = resolve(args.cloudProjectDir, '..', 'mainSite');
  }

  return args;
}

const { cloudProjectDir, mainSiteProjectDir } = parseArgs(process.argv.slice(2));
const identityDtoRoot = 'identityApi/src/main/java/com/alicia/cloudstorage/identity/dto';
const identityControllerRoot = 'identityApi/src/main/java/com/alicia/cloudstorage/identity/controller';
const userSiteTypesSource = readProjectFile(mainSiteProjectDir, 'userSite/src/types.ts');

function readProjectFile(projectDir, relativePath) {
  const path = resolve(projectDir, relativePath);
  assert.ok(existsSync(path), `Missing contract file: ${path}`);
  return readFileSync(path, 'utf8');
}

function readIdentityFile(relativePath) {
  return readProjectFile(cloudProjectDir, relativePath);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

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

function extractJavaRecordFields(relativePath, recordName) {
  const source = readIdentityFile(relativePath);
  const recordPattern = new RegExp(`record\\s+${escapeRegExp(recordName)}(?:\\s*<[^>]+>)?\\s*\\(`);
  const match = recordPattern.exec(source);
  assert.ok(match, `${recordName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');

  return source
    .slice(openIndex + 1, closeIndex)
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/,$/, ''))
    .filter((line) => line && !line.startsWith('@'))
    .map((line) => {
      const fieldMatch = /([A-Za-z][A-Za-z0-9_]*)$/.exec(line);
      assert.ok(fieldMatch, `Unable to parse ${recordName} field line: ${line}`);
      return fieldMatch[1];
    });
}

function extractTsTypeBlock(typeName) {
  const typePattern = new RegExp(`export\\s+type\\s+${escapeRegExp(typeName)}(?:\\s*<[^>]+>)?\\s*=`);
  const match = typePattern.exec(userSiteTypesSource);
  assert.ok(match, `${typeName} must exist in mainSite/userSite/src/types.ts`);

  const openIndex = userSiteTypesSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${typeName} must use an object type block`);

  const closeIndex = findMatching(userSiteTypesSource, openIndex, '{', '}');
  return userSiteTypesSource.slice(openIndex + 1, closeIndex);
}

function extractTsTypeFields(typeName) {
  const fields = [];
  let depth = 0;

  for (const line of extractTsTypeBlock(typeName).split(/\r?\n/)) {
    const trimmed = line.trim();

    if (depth === 0) {
      const fieldMatch = /^([A-Za-z][A-Za-z0-9_]*)\??:/.exec(trimmed);
      if (fieldMatch) {
        fields.push(fieldMatch[1]);
      }
    }

    for (const char of line) {
      if (char === '{') {
        depth += 1;
      } else if (char === '}') {
        depth -= 1;
      }
    }
  }

  return fields;
}

function sortedUnique(fields) {
  return [...new Set(fields)].sort();
}

function assertSameFields(label, actualFields, expectedFields) {
  assert.deepEqual(sortedUnique(actualFields), sortedUnique(expectedFields), `${label} fields must match`);
}

function extractRequestParamsForMethod(relativePath, methodName) {
  const source = readIdentityFile(relativePath);
  const methodPattern = new RegExp(`${escapeRegExp(methodName)}\\s*\\(`);
  const match = methodPattern.exec(source);
  assert.ok(match, `${methodName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');

  return source
    .slice(openIndex + 1, closeIndex)
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/,$/, ''))
    .filter((line) => line.includes('@RequestParam'))
    .map((line) => {
      const paramMatch = /([A-Za-z][A-Za-z0-9_]*)$/.exec(line);
      assert.ok(paramMatch, `Unable to parse request parameter line: ${line}`);
      return paramMatch[1];
    });
}

assertSameFields(
  'IdentityUser',
  extractTsTypeFields('IdentityUser'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityUserResponse.java`, 'IdentityUserResponse'),
);
assertSameFields(
  'IdentityLoginResponse',
  extractTsTypeFields('IdentityLoginResponse'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityLoginResponse.java`, 'IdentityLoginResponse'),
);
assertSameFields(
  'IdentitySession',
  extractTsTypeFields('IdentitySession'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentitySessionResponse.java`, 'IdentitySessionResponse'),
);
assertSameFields(
  'IdentityApplicationRole',
  extractTsTypeFields('IdentityApplicationRole'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityApplicationRoleResponse.java`, 'IdentityApplicationRoleResponse'),
);
assertSameFields(
  'IdentityAuditLog',
  extractTsTypeFields('IdentityAuditLog'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityAuditLogResponse.java`, 'IdentityAuditLogResponse'),
);
assertSameFields(
  'IdentityAuditLogPage',
  extractTsTypeFields('IdentityAuditLogPage'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityAuditLogPageResponse.java`, 'IdentityAuditLogPageResponse'),
);
assertSameFields(
  'CreateIdentityUserPayload',
  extractTsTypeFields('CreateIdentityUserPayload'),
  extractJavaRecordFields(`${identityDtoRoot}/AdminCreateIdentityUserRequest.java`, 'AdminCreateIdentityUserRequest'),
);
assertSameFields(
  'ResetUserPasswordPayload',
  extractTsTypeFields('ResetUserPasswordPayload'),
  extractJavaRecordFields(`${identityDtoRoot}/AdminResetUserPasswordRequest.java`, 'AdminResetUserPasswordRequest'),
);
assertSameFields(
  'UpdateIdentityApplicationRolePayload',
  extractTsTypeFields('UpdateIdentityApplicationRolePayload'),
  extractJavaRecordFields(`${identityDtoRoot}/UpdateIdentityApplicationRoleRequest.java`, 'UpdateIdentityApplicationRoleRequest'),
);
assertSameFields(
  'UpdateIdentityProfilePayload',
  extractTsTypeFields('UpdateIdentityProfilePayload'),
  extractJavaRecordFields(`${identityDtoRoot}/UpdateIdentityProfileRequest.java`, 'UpdateIdentityProfileRequest'),
);
assertSameFields(
  'IdentityAuditLogQuery',
  extractTsTypeFields('IdentityAuditLogQuery'),
  extractRequestParamsForMethod(`${identityControllerRoot}/IdentityAdminAuditLogController.java`, 'listAuditLogs'),
);
assertSameFields(
  'IdentitySessionsQuery',
  ['includeRevoked'],
  extractRequestParamsForMethod(`${identityControllerRoot}/IdentityAuthController.java`, 'listSessions'),
);

console.log('[OK] identity console IdentityApi contracts verified');
