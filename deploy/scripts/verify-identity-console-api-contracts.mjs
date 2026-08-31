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
const userSiteApiSource = readProjectFile(mainSiteProjectDir, 'userSite/src/lib/api.ts');

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

function assertControllerBasePath(relativePath, expectedBasePath) {
  const source = readIdentityFile(relativePath);
  const pattern = new RegExp(`@RequestMapping\\("${escapeRegExp(expectedBasePath)}"\\)`);
  assert.match(source, pattern, `${relativePath} must keep ${expectedBasePath}`);
}

function assertControllerMethodMapping(relativePath, methodName, mappingPattern) {
  const source = readIdentityFile(relativePath);
  const methodIndex = source.indexOf(`${methodName}(`);
  assert.ok(methodIndex > -1, `${methodName} must exist in ${relativePath}`);

  const context = source.slice(Math.max(0, methodIndex - 260), methodIndex);
  assert.match(context, mappingPattern, `${methodName} must keep its IdentityApi route mapping`);
}

function extractTsFunctionSource(functionName) {
  const functionPattern = new RegExp(`export\\s+function\\s+${escapeRegExp(functionName)}\\s*\\(`);
  const match = functionPattern.exec(userSiteApiSource);
  assert.ok(match, `${functionName} must exist in mainSite/userSite/src/lib/api.ts`);

  const openIndex = userSiteApiSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${functionName} must use a function block`);

  const closeIndex = findMatching(userSiteApiSource, openIndex, '{', '}');
  return userSiteApiSource.slice(openIndex + 1, closeIndex);
}

function assertUserSiteApiEndpoint(functionName, endpointNeedle) {
  const source = extractTsFunctionSource(functionName);
  assert.ok(
    source.includes(endpointNeedle),
    `${functionName} must call ${endpointNeedle} from mainSite/userSite/src/lib/api.ts`,
  );
}

function assertUserSiteApiMethod(functionName, method) {
  const source = extractTsFunctionSource(functionName);
  assert.match(source, new RegExp(`method:\\s*'${escapeRegExp(method)}'`), `${functionName} must use ${method}`);
}

function assertIdentityEndpointContract(contract) {
  assertControllerBasePath(contract.controller, contract.basePath);
  assertControllerMethodMapping(contract.controller, contract.javaMethod, contract.mappingPattern);
  assertUserSiteApiEndpoint(contract.tsFunction, contract.endpointNeedle);

  if (contract.httpMethod) {
    assertUserSiteApiMethod(contract.tsFunction, contract.httpMethod);
  }
}

const identityEndpointContracts = [
  {
    controller: `${identityControllerRoot}/IdentityAuthController.java`,
    basePath: '/api/identity/auth',
    javaMethod: 'me',
    mappingPattern: /@GetMapping\("\/me"\)/,
    tsFunction: 'fetchCurrentIdentityUser',
    endpointNeedle: "'/api/identity/auth/me'",
  },
  {
    controller: `${identityControllerRoot}/IdentityAuthController.java`,
    basePath: '/api/identity/auth',
    javaMethod: 'updateProfile',
    mappingPattern: /@PutMapping\("\/profile"\)/,
    tsFunction: 'updateIdentityProfile',
    endpointNeedle: "'/api/identity/auth/profile'",
    httpMethod: 'PUT',
  },
  {
    controller: `${identityControllerRoot}/IdentityAuthController.java`,
    basePath: '/api/identity/auth',
    javaMethod: 'refreshToken',
    mappingPattern: /@PostMapping\("\/token\/refresh"\)/,
    tsFunction: 'refreshAuthSession',
    endpointNeedle: "'/api/identity/auth/token/refresh'",
    httpMethod: 'POST',
  },
  {
    controller: `${identityControllerRoot}/IdentityAuthController.java`,
    basePath: '/api/identity/auth',
    javaMethod: 'logout',
    mappingPattern: /@PostMapping\("\/logout"\)/,
    tsFunction: 'logoutAuthToken',
    endpointNeedle: "'/api/identity/auth/logout'",
    httpMethod: 'POST',
  },
  {
    controller: `${identityControllerRoot}/IdentityAuthController.java`,
    basePath: '/api/identity/auth',
    javaMethod: 'listSessions',
    mappingPattern: /@GetMapping\("\/sessions"\)/,
    tsFunction: 'fetchIdentitySessions',
    endpointNeedle: '`/api/identity/auth/sessions${suffix}`',
  },
  {
    controller: `${identityControllerRoot}/IdentityAuthController.java`,
    basePath: '/api/identity/auth',
    javaMethod: 'revokeSession',
    mappingPattern: /@DeleteMapping\("\/sessions\/\{sessionId\}"\)/,
    tsFunction: 'revokeIdentitySession',
    endpointNeedle: '`/api/identity/auth/sessions/${sessionId}`',
    httpMethod: 'DELETE',
  },
  {
    controller: `${identityControllerRoot}/IdentityAdminUserController.java`,
    basePath: '/api/identity/admin/users',
    javaMethod: 'listUsers',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchIdentityUsers',
    endpointNeedle: "'/api/identity/admin/users'",
  },
  {
    controller: `${identityControllerRoot}/IdentityAdminUserController.java`,
    basePath: '/api/identity/admin/users',
    javaMethod: 'createUser',
    mappingPattern: /@PostMapping\b/,
    tsFunction: 'createIdentityUser',
    endpointNeedle: "'/api/identity/admin/users'",
    httpMethod: 'POST',
  },
  {
    controller: `${identityControllerRoot}/IdentityAdminUserController.java`,
    basePath: '/api/identity/admin/users',
    javaMethod: 'resetUserPassword',
    mappingPattern: /@PutMapping\("\/\{userId\}\/password"\)/,
    tsFunction: 'resetIdentityUserPassword',
    endpointNeedle: '`/api/identity/admin/users/${userId}/password`',
    httpMethod: 'PUT',
  },
  {
    controller: `${identityControllerRoot}/IdentityAdminApplicationRoleController.java`,
    basePath: '/api/identity/admin/users/{userId}/app-roles',
    javaMethod: 'listUserRoles',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchIdentityApplicationRoles',
    endpointNeedle: '`/api/identity/admin/users/${userId}/app-roles`',
  },
  {
    controller: `${identityControllerRoot}/IdentityAdminApplicationRoleController.java`,
    basePath: '/api/identity/admin/users/{userId}/app-roles',
    javaMethod: 'updateUserRole',
    mappingPattern: /@PutMapping\("\/\{appCode\}"\)/,
    tsFunction: 'updateIdentityApplicationRole',
    endpointNeedle: '`/api/identity/admin/users/${userId}/app-roles/${encodeURIComponent(appCode)}`',
    httpMethod: 'PUT',
  },
  {
    controller: `${identityControllerRoot}/IdentityAdminAuditLogController.java`,
    basePath: '/api/identity/admin/audit-logs',
    javaMethod: 'listAuditLogs',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchIdentityAuditLogs',
    endpointNeedle: '`/api/identity/admin/audit-logs${suffix}`',
  },
];

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

for (const contract of identityEndpointContracts) {
  assertIdentityEndpointContract(contract);
}

console.log('[OK] identity console IdentityApi contracts verified');
