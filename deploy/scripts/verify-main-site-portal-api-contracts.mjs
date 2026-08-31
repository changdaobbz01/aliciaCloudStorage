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
const cloudControllerRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller';
const mainSiteAuthSource = readProjectFile(mainSiteProjectDir, 'webApp/src/auth.ts');
const identityAvatarSource = readProjectFile(mainSiteProjectDir, 'webApp/src/IdentityAvatar.tsx');

function readProjectFile(projectDir, relativePath) {
  const path = resolve(projectDir, relativePath);
  assert.ok(existsSync(path), `Missing contract file: ${path}`);
  return readFileSync(path, 'utf8');
}

function readCloudProjectFile(relativePath) {
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

function stripLeadingJavaAnnotations(line) {
  let current = line.trim().replace(/,$/, '').trim();
  let previous;

  do {
    previous = current;
    current = current.replace(/^@[A-Za-z][A-Za-z0-9_.]*(?:\([^)]*\))?\s*/, '').trim();
  } while (current !== previous);

  return current;
}

function extractJavaRecordFields(relativePath, recordName) {
  const source = readCloudProjectFile(relativePath);
  const recordPattern = new RegExp(`record\\s+${escapeRegExp(recordName)}(?:\\s*<[^>]+>)?\\s*\\(`);
  const match = recordPattern.exec(source);
  assert.ok(match, `${recordName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');

  return source
    .slice(openIndex + 1, closeIndex)
    .split(/\r?\n/)
    .map(stripLeadingJavaAnnotations)
    .filter(Boolean)
    .map((line) => {
      const fieldMatch = /([A-Za-z][A-Za-z0-9_]*)$/.exec(line);
      assert.ok(fieldMatch, `Unable to parse ${recordName} field line: ${line}`);
      return fieldMatch[1];
    });
}

function extractTsTypeBlock(typeName) {
  const typePattern = new RegExp(`export\\s+type\\s+${escapeRegExp(typeName)}(?:\\s*<[^>]+>)?\\s*=`);
  const match = typePattern.exec(mainSiteAuthSource);
  assert.ok(match, `${typeName} must exist in mainSite/webApp/src/auth.ts`);

  const openIndex = mainSiteAuthSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${typeName} must use an object type block`);

  const closeIndex = findMatching(mainSiteAuthSource, openIndex, '{', '}');
  return mainSiteAuthSource.slice(openIndex + 1, closeIndex);
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

function extractRequestParamsForMethod(relativePath, methodName) {
  const source = readCloudProjectFile(relativePath);
  const methodPattern = new RegExp(`${escapeRegExp(methodName)}\\s*\\(`);
  const match = methodPattern.exec(source);
  assert.ok(match, `${methodName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');
  const parameterList = source.slice(openIndex + 1, closeIndex);
  const requestParams = [];
  const requestParamPattern = /@RequestParam(?:\([^)]*\))?\s+[A-Za-z0-9_.<>?]+\s+([A-Za-z][A-Za-z0-9_]*)/g;
  let paramMatch;

  while ((paramMatch = requestParamPattern.exec(parameterList)) !== null) {
    requestParams.push(paramMatch[1]);
  }

  return requestParams;
}

function extractTsFunctionSource(functionName) {
  const functionPattern = new RegExp(`export\\s+(?:async\\s+)?function\\s+${escapeRegExp(functionName)}\\s*\\(`);
  const match = functionPattern.exec(mainSiteAuthSource);
  assert.ok(match, `${functionName} must exist in mainSite/webApp/src/auth.ts`);

  const openIndex = mainSiteAuthSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${functionName} must use a function block`);

  const closeIndex = findMatching(mainSiteAuthSource, openIndex, '{', '}');
  return mainSiteAuthSource.slice(openIndex + 1, closeIndex);
}

function assertControllerBasePath(relativePath, expectedBasePath) {
  const source = readCloudProjectFile(relativePath);
  const pattern = new RegExp(`@RequestMapping\\("${escapeRegExp(expectedBasePath)}"\\)`);
  assert.match(source, pattern, `${relativePath} must keep ${expectedBasePath}`);
}

function assertControllerMethodMapping(relativePath, methodName, mappingPattern) {
  const source = readCloudProjectFile(relativePath);
  const methodIndex = source.indexOf(`${methodName}(`);
  assert.ok(methodIndex > -1, `${methodName} must exist in ${relativePath}`);

  const context = source.slice(Math.max(0, methodIndex - 260), methodIndex);
  assert.match(context, mappingPattern, `${methodName} must keep its backend route mapping`);
}

function assertSourceUsesPath(label, source, endpointNeedle) {
  assert.ok(source.includes(endpointNeedle), `${label} must call ${endpointNeedle}`);
}

function assertMainSiteApiEndpoint(functionName, endpointNeedle) {
  assertSourceUsesPath(`${functionName} in mainSite/webApp/src/auth.ts`, extractTsFunctionSource(functionName), endpointNeedle);
}

function assertMainSiteApiMethod(functionName, method) {
  const source = extractTsFunctionSource(functionName);
  assert.match(source, new RegExp(`method:\\s*'${escapeRegExp(method)}'`), `${functionName} must use ${method}`);
}

function extractJavaMethodParts(relativePath, methodName) {
  const source = readCloudProjectFile(relativePath);
  const methodPattern = new RegExp(`${escapeRegExp(methodName)}\\s*\\(`);
  const match = methodPattern.exec(source);
  assert.ok(match, `${methodName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');
  const bodyOpenIndex = source.indexOf('{', closeIndex);
  assert.ok(bodyOpenIndex > -1, `${methodName} must use a method body in ${relativePath}`);

  const bodyCloseIndex = findMatching(source, bodyOpenIndex, '{', '}');
  return {
    parameters: source.slice(openIndex + 1, closeIndex),
    body: source.slice(bodyOpenIndex + 1, bodyCloseIndex),
  };
}

function assertMainSiteApiUsesAuthToken(functionName) {
  const source = extractTsFunctionSource(functionName);
  assert.match(source, /withToken\((?:token|accessToken)\b/, `${functionName} must send the current auth token`);
}

function assertControllerMethodReceivesAuthorization(relativePath, methodName) {
  const { parameters, body } = extractJavaMethodParts(relativePath, methodName);

  assert.match(
    parameters,
    /@RequestHeader\s*\(\s*value\s*=\s*HttpHeaders\.AUTHORIZATION\s*,\s*required\s*=\s*false\s*\)\s+String\s+authorization/,
    `${methodName} must receive the Authorization header`,
  );
  assert.match(body, /\bauthorization\b/, `${methodName} must pass Authorization to the service layer`);
}

function assertCloudProfileAvatarInterceptorContract() {
  const source = readCloudProjectFile('CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/config/WebMvcConfig.java');
  assert.match(
    source,
    /registry\.addInterceptor\(currentPrincipalInterceptor\)[\s\S]*\.addPathPatterns\([\s\S]*"\/api\/cloud-profile\/avatar"[\s\S]*\)/,
    'CloudStorageApi must protect avatar uploads with CurrentPrincipalInterceptor',
  );
}

function assertMainSitePortalEndpointContract(contract) {
  assertControllerBasePath(contract.controller, contract.basePath);
  assertControllerMethodMapping(contract.controller, contract.javaMethod, contract.mappingPattern);
  assertMainSiteApiEndpoint(contract.tsFunction, contract.endpointNeedle);

  if (contract.requiresAuthToken) {
    assertMainSiteApiUsesAuthToken(contract.tsFunction);
  }

  if (contract.requiresAuthorizationHeader) {
    assertControllerMethodReceivesAuthorization(contract.controller, contract.javaMethod);
  }

  if (contract.httpMethod) {
    assertMainSiteApiMethod(contract.tsFunction, contract.httpMethod);
  }
}

function sortedUnique(fields) {
  return [...new Set(fields)].sort();
}

function assertSameFields(label, actualFields, expectedFields) {
  assert.deepEqual(sortedUnique(actualFields), sortedUnique(expectedFields), `${label} fields must match`);
}

function assertFieldsSubset(label, actualFields, expectedFields) {
  const missingFields = sortedUnique(actualFields).filter((field) => !expectedFields.includes(field));
  assert.deepEqual(missingFields, [], `${label} fields must be accepted by the backend contract`);
}

function assertContainsFields(label, actualFields, expectedFields) {
  const missingFields = sortedUnique(expectedFields).filter((field) => !actualFields.includes(field));
  assert.deepEqual(missingFields, [], `${label} must include required fields`);
}

const identityAuthController = `${identityControllerRoot}/IdentityAuthController.java`;
const cloudProfileController = `${cloudControllerRoot}/CloudProfileController.java`;

const mainSitePortalEndpointContracts = [
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'me',
    mappingPattern: /@GetMapping\("\/me"\)/,
    tsFunction: 'fetchCurrentIdentityUser',
    endpointNeedle: '/api/identity/auth/me',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'refreshToken',
    mappingPattern: /@PostMapping\("\/token\/refresh"\)/,
    tsFunction: 'refreshAuthSession',
    endpointNeedle: '/api/identity/auth/token/refresh',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'listSessions',
    mappingPattern: /@GetMapping\("\/sessions"\)/,
    tsFunction: 'fetchIdentitySessions',
    endpointNeedle: '/api/identity/auth/sessions',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'revokeSession',
    mappingPattern: /@DeleteMapping\("\/sessions\/\{sessionId\}"\)/,
    tsFunction: 'revokeIdentitySession',
    endpointNeedle: '/api/identity/auth/sessions/',
    httpMethod: 'DELETE',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'logout',
    mappingPattern: /@PostMapping\("\/logout"\)/,
    tsFunction: 'logoutCurrentIdentitySession',
    endpointNeedle: '/api/identity/auth/logout',
    httpMethod: 'POST',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'login',
    mappingPattern: /@PostMapping\("\/login"\)/,
    tsFunction: 'login',
    endpointNeedle: '/api/identity/auth/login',
    httpMethod: 'POST',
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'requestEmailRegistrationCode',
    mappingPattern: /@PostMapping\("\/register\/email-code"\)/,
    tsFunction: 'requestEmailRegistrationCode',
    endpointNeedle: '/api/identity/auth/register/email-code',
    httpMethod: 'POST',
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'verifyEmailRegistration',
    mappingPattern: /@PostMapping\("\/register\/verify"\)/,
    tsFunction: 'verifyEmailRegistration',
    endpointNeedle: '/api/identity/auth/register/verify',
    httpMethod: 'POST',
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'updateProfile',
    mappingPattern: /@PutMapping\("\/profile"\)/,
    tsFunction: 'updateIdentityProfile',
    endpointNeedle: '/api/identity/auth/profile',
    httpMethod: 'PUT',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
  {
    controller: identityAuthController,
    basePath: '/api/identity/auth',
    javaMethod: 'changePassword',
    mappingPattern: /@PutMapping\("\/password"\)/,
    tsFunction: 'changeIdentityPassword',
    endpointNeedle: '/api/identity/auth/password',
    httpMethod: 'PUT',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'uploadAvatar',
    mappingPattern: /@PostMapping\(value = "\/avatar", consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
    tsFunction: 'uploadIdentityAvatar',
    endpointNeedle: '/api/cloud-profile/avatar',
    httpMethod: 'POST',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
];

assertSameFields(
  'main site IdentityUser',
  extractTsTypeFields('IdentityUser'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityUserResponse.java`, 'IdentityUserResponse'),
);
assertSameFields(
  'main site LoginResponse',
  extractTsTypeFields('LoginResponse'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityLoginResponse.java`, 'IdentityLoginResponse'),
);
assertSameFields(
  'main site IdentitySession',
  extractTsTypeFields('IdentitySession'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentitySessionResponse.java`, 'IdentitySessionResponse'),
);
assertSameFields(
  'main site registration email code request',
  extractTsTypeFields('RequestEmailRegistrationCodePayload'),
  extractJavaRecordFields(`${identityDtoRoot}/RequestEmailRegistrationCodeRequest.java`, 'RequestEmailRegistrationCodeRequest'),
);
assertSameFields(
  'main site email registration verification request',
  extractTsTypeFields('VerifyEmailRegistrationPayload'),
  extractJavaRecordFields(`${identityDtoRoot}/VerifyEmailRegistrationRequest.java`, 'VerifyEmailRegistrationRequest'),
);
assertSameFields(
  'main site identity profile update request',
  extractTsTypeFields('UpdateIdentityProfilePayload'),
  extractJavaRecordFields(`${identityDtoRoot}/UpdateIdentityProfileRequest.java`, 'UpdateIdentityProfileRequest'),
);
assertSameFields(
  'main site password change request',
  extractTsTypeFields('ChangeIdentityPasswordPayload'),
  extractJavaRecordFields(`${identityDtoRoot}/ChangePasswordRequest.java`, 'ChangePasswordRequest'),
);

const loginRequestFields = extractTsTypeFields('LoginPayload');
const identityLoginRequestFields = extractJavaRecordFields(`${identityDtoRoot}/IdentityLoginRequest.java`, 'IdentityLoginRequest');
assertFieldsSubset('main site LoginPayload', loginRequestFields, identityLoginRequestFields);
assertContainsFields('main site LoginPayload', loginRequestFields, ['identifier', 'password']);
assertContainsFields('IdentityLoginRequest', identityLoginRequestFields, ['identifier', 'password']);

assertSameFields(
  'main site refreshAuthSession request body',
  ['refreshToken'],
  extractJavaRecordFields(`${identityDtoRoot}/IdentityRefreshTokenRequest.java`, 'IdentityRefreshTokenRequest'),
);
assertFieldsSubset(
  'main site logoutCurrentIdentitySession request body',
  ['refreshToken'],
  extractJavaRecordFields(`${identityDtoRoot}/IdentityLogoutRequest.java`, 'IdentityLogoutRequest'),
);
assertSameFields(
  'main site fetchIdentitySessions query parameters',
  ['includeRevoked'],
  extractRequestParamsForMethod(identityAuthController, 'listSessions'),
);
assert.match(
  extractTsFunctionSource('fetchIdentitySessions'),
  /includeRevoked=true/,
  'fetchIdentitySessions must keep the includeRevoked query parameter',
);

for (const contract of mainSitePortalEndpointContracts) {
  assertMainSitePortalEndpointContract(contract);
}

assertControllerBasePath(cloudProfileController, '/api/cloud-profile');
assertControllerMethodMapping(cloudProfileController, 'getAvatar', /@GetMapping\("\/avatar\/\{userId\}"\)/);
assertCloudProfileAvatarInterceptorContract();
assertSourceUsesPath(
  'IdentityAvatar in mainSite/webApp/src/IdentityAvatar.tsx',
  identityAvatarSource,
  '/api/cloud-profile/avatar/',
);

console.log('[OK] main site portal API contracts verified');
