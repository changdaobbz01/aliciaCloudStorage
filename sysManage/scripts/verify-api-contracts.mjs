import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const typesSource = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8');
const apiSource = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8');
const driveSharedSource = readFileSync(new URL('../src/features/drive/driveShared.ts', import.meta.url), 'utf8');

function readRepoFile(relativePath) {
  return readFileSync(new URL(`../../${relativePath}`, import.meta.url), 'utf8');
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
  const source = readRepoFile(relativePath);
  const recordPattern = new RegExp(`record\\s+${escapeRegExp(recordName)}(?:\\s*<[^>]+>)?\\s*\\(`);
  const match = recordPattern.exec(source);
  assert.ok(match, `${recordName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');
  const parameterList = source.slice(openIndex + 1, closeIndex);

  return parameterList
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
  const match = typePattern.exec(typesSource);
  assert.ok(match, `${typeName} must exist in sysManage/src/types.ts`);

  const openIndex = typesSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${typeName} must use an object type block`);

  const closeIndex = findMatching(typesSource, openIndex, '{', '}');
  return typesSource.slice(openIndex + 1, closeIndex);
}

function extractTsFieldsFromBlock(block) {
  const fields = [];
  let depth = 0;

  for (const line of block.split(/\r?\n/)) {
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

function extractTsTypeFields(typeName) {
  return extractTsFieldsFromBlock(extractTsTypeBlock(typeName));
}

function extractTsNestedObjectFields(typeName, propertyName) {
  const block = extractTsTypeBlock(typeName);
  const propertyPattern = new RegExp(`(^|\\n)\\s*${escapeRegExp(propertyName)}\\s*:\\s*\\{`);
  const match = propertyPattern.exec(block);
  assert.ok(match, `${typeName}.${propertyName} must use an inline object type`);

  const openIndex = block.indexOf('{', match.index);
  const closeIndex = findMatching(block, openIndex, '{', '}');
  return extractTsFieldsFromBlock(block.slice(openIndex + 1, closeIndex));
}

function sortedUnique(fields) {
  return [...new Set(fields)].sort();
}

function assertSameFields(label, actualFields, expectedFields) {
  assert.deepEqual(sortedUnique(actualFields), sortedUnique(expectedFields), `${label} fields must match`);
}

function extractRequestParamsForMethod(relativePath, methodName) {
  const source = readRepoFile(relativePath);
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

function extractApiFunctionBody(functionName) {
  const functionPattern = new RegExp(`export\\s+function\\s+${escapeRegExp(functionName)}\\s*\\(`);
  const match = functionPattern.exec(apiSource);
  assert.ok(match, `${functionName} must exist in sysManage/src/lib/api.ts`);

  const openIndex = apiSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${functionName} must use a function body`);

  const closeIndex = findMatching(apiSource, openIndex, '{', '}');
  return apiSource.slice(openIndex + 1, closeIndex);
}

function assertControllerBasePath(relativePath, expectedBasePath) {
  if (!expectedBasePath) {
    return;
  }

  const source = readRepoFile(relativePath);
  const pattern = new RegExp(`@RequestMapping\\("${escapeRegExp(expectedBasePath)}"\\)`);
  assert.match(source, pattern, `${relativePath} must keep ${expectedBasePath}`);
}

function assertControllerMethodMapping(relativePath, methodName, mappingPattern) {
  const source = readRepoFile(relativePath);
  const methodIndex = source.indexOf(`${methodName}(`);
  assert.ok(methodIndex > -1, `${methodName} must exist in ${relativePath}`);

  const context = source.slice(Math.max(0, methodIndex - 260), methodIndex);
  assert.match(context, mappingPattern, `${methodName} must keep its CloudStorageApi route mapping`);
}

function assertApiFunctionUsesPath(functionName, pathNeedle) {
  assert.ok(
    extractApiFunctionBody(functionName).includes(pathNeedle),
    `${functionName} must call ${pathNeedle} from sysManage/src/lib/api.ts`,
  );
}

function assertApiFunctionUsesMethod(functionName, method) {
  assert.match(
    extractApiFunctionBody(functionName),
    new RegExp(`method:\\s*'${escapeRegExp(method)}'`),
    `${functionName} must use ${method}`,
  );
}

function assertApiUploadFunctionUsesPost(functionName) {
  assert.match(extractApiFunctionBody(functionName), /requestUploadJson</, `${functionName} must use the upload request helper`);
  assert.match(apiSource, /xhr\.open\('POST', url\)/, 'sysManage upload request helper must use POST');
}

function assertCloudConsoleEndpointContract(contract) {
  assertControllerBasePath(contract.controller, contract.basePath);
  assertControllerMethodMapping(contract.controller, contract.javaMethod, contract.mappingPattern);
  assertApiFunctionUsesPath(contract.tsFunction, contract.endpointNeedle);

  if (contract.httpMethod) {
    assertApiFunctionUsesMethod(contract.tsFunction, contract.httpMethod);
  }

  if (contract.uploadPost) {
    assertApiUploadFunctionUsesPost(contract.tsFunction);
  }
}

const dtoRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/dto';
const controllerRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller';
const healthController = `${controllerRoot}/HealthController.java`;
const cloudProfileController = `${controllerRoot}/CloudProfileController.java`;
const adminCloudUserController = `${controllerRoot}/AdminCloudUserController.java`;
const adminCloudUserProfileController = `${controllerRoot}/AdminCloudUserProfileController.java`;
const operationsController = `${controllerRoot}/AdminCloudOperationsController.java`;
const adminAppPackageController = `${controllerRoot}/AdminAppPackageController.java`;
const appPackageController = `${controllerRoot}/AppPackageController.java`;
const overviewDto = `${dtoRoot}/AdminCloudOperationsOverviewResponse.java`;

const cloudConsoleEndpointContracts = [
  {
    controller: healthController,
    javaMethod: 'health',
    mappingPattern: /@GetMapping\("\/api\/health"\)/,
    tsFunction: 'fetchHealth',
    endpointNeedle: "'/api/health'",
  },
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'me',
    mappingPattern: /@GetMapping\("\/me"\)/,
    tsFunction: 'fetchCurrentUser',
    endpointNeedle: "'/api/cloud-profile/me'",
  },
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'uploadAvatar',
    mappingPattern: /@PostMapping\(value = "\/avatar", consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
    tsFunction: 'uploadCurrentUserAvatar',
    endpointNeedle: "'/api/cloud-profile/avatar'",
    uploadPost: true,
  },
  {
    controller: adminCloudUserController,
    basePath: '/api/admin/cloud-users',
    javaMethod: 'listUsers',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchUsers',
    endpointNeedle: "'/api/admin/cloud-users'",
  },
  {
    controller: adminCloudUserProfileController,
    basePath: '/api/admin/cloud-users',
    javaMethod: 'updateUserQuota',
    mappingPattern: /@PutMapping\("\/\{userId\}\/quota"\)/,
    tsFunction: 'updateUserStorageQuota',
    endpointNeedle: '`/api/admin/cloud-users/${userId}/quota`',
    httpMethod: 'PUT',
  },
  {
    controller: operationsController,
    basePath: '/api/admin/cloud-operations',
    javaMethod: 'getOverview',
    mappingPattern: /@GetMapping\("\/overview"\)/,
    tsFunction: 'fetchAdminCloudOperationsOverview',
    endpointNeedle: "'/api/admin/cloud-operations/overview'",
  },
  {
    controller: operationsController,
    basePath: '/api/admin/cloud-operations',
    javaMethod: 'listShareLinks',
    mappingPattern: /@GetMapping\("\/shares"\)/,
    tsFunction: 'fetchAdminCloudOperationShares',
    endpointNeedle: '`/api/admin/cloud-operations/shares${toQuerySuffix(search)}`',
  },
  {
    controller: operationsController,
    basePath: '/api/admin/cloud-operations',
    javaMethod: 'listTrashNodes',
    mappingPattern: /@GetMapping\("\/trash"\)/,
    tsFunction: 'fetchAdminCloudOperationTrash',
    endpointNeedle: '`/api/admin/cloud-operations/trash${toQuerySuffix(search)}`',
  },
  {
    controller: operationsController,
    basePath: '/api/admin/cloud-operations',
    javaMethod: 'listStorageUsers',
    mappingPattern: /@GetMapping\("\/users\/storage"\)/,
    tsFunction: 'fetchAdminCloudStorageUsers',
    endpointNeedle: '`/api/admin/cloud-operations/users/storage${toQuerySuffix(search)}`',
  },
  {
    controller: appPackageController,
    basePath: '/api/app-package',
    javaMethod: 'getCurrentPackageInfo',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchPublicAppPackage',
    endpointNeedle: "'/api/app-package'",
  },
  {
    controller: adminAppPackageController,
    basePath: '/api/admin/app-package',
    javaMethod: 'getCurrentPackageInfo',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchAdminAppPackage',
    endpointNeedle: "'/api/admin/app-package'",
  },
  {
    controller: adminAppPackageController,
    basePath: '/api/admin/app-package',
    javaMethod: 'uploadCurrentPackage',
    mappingPattern: /@PostMapping\(consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
    tsFunction: 'uploadAdminAppPackage',
    endpointNeedle: "'/api/admin/app-package'",
    uploadPost: true,
  },
  {
    controller: adminAppPackageController,
    basePath: '/api/admin/app-package',
    javaMethod: 'deleteCurrentPackage',
    mappingPattern: /@DeleteMapping\b/,
    tsFunction: 'deleteAdminAppPackage',
    endpointNeedle: "'/api/admin/app-package'",
    httpMethod: 'DELETE',
  },
];

assertSameFields(
  'User',
  extractTsTypeFields('User'),
  extractJavaRecordFields(`${dtoRoot}/UserProfileResponse.java`, 'UserProfileResponse'),
);
assertSameFields(
  'AdminCloudOperationsOverview',
  extractTsTypeFields('AdminCloudOperationsOverview'),
  extractJavaRecordFields(overviewDto, 'AdminCloudOperationsOverviewResponse'),
);
assertSameFields(
  'AdminCloudOperationsOverview.capacity',
  extractTsNestedObjectFields('AdminCloudOperationsOverview', 'capacity'),
  extractJavaRecordFields(overviewDto, 'CapacityOverview'),
);
assertSameFields(
  'AdminCloudOperationsOverview.activeNodes',
  extractTsNestedObjectFields('AdminCloudOperationsOverview', 'activeNodes'),
  extractJavaRecordFields(overviewDto, 'NodeOverview'),
);
assertSameFields(
  'AdminCloudOperationsOverview.trash',
  extractTsNestedObjectFields('AdminCloudOperationsOverview', 'trash'),
  extractJavaRecordFields(overviewDto, 'TrashOverview'),
);
assertSameFields(
  'AdminCloudOperationsOverview.shares',
  extractTsNestedObjectFields('AdminCloudOperationsOverview', 'shares'),
  extractJavaRecordFields(overviewDto, 'ShareOverview'),
);
assertSameFields(
  'AdminCloudOperationsOverview.multipartUploads',
  extractTsNestedObjectFields('AdminCloudOperationsOverview', 'multipartUploads'),
  extractJavaRecordFields(overviewDto, 'MultipartUploadOverview'),
);
assertSameFields(
  'AdminCloudShareLink',
  extractTsTypeFields('AdminCloudShareLink'),
  extractJavaRecordFields(`${dtoRoot}/AdminCloudShareLinkResponse.java`, 'AdminCloudShareLinkResponse'),
);
assertSameFields(
  'AdminCloudTrashNode',
  extractTsTypeFields('AdminCloudTrashNode'),
  extractJavaRecordFields(`${dtoRoot}/AdminCloudTrashNodeResponse.java`, 'AdminCloudTrashNodeResponse'),
);
assertSameFields(
  'AdminCloudStorageUserUsage',
  extractTsTypeFields('AdminCloudStorageUserUsage'),
  extractJavaRecordFields(`${dtoRoot}/AdminCloudStorageUserUsageResponse.java`, 'AdminCloudStorageUserUsageResponse'),
);
assertSameFields(
  'PageResponse',
  extractTsTypeFields('PageResponse'),
  extractJavaRecordFields(`${dtoRoot}/PageResponse.java`, 'PageResponse'),
);
assertSameFields(
  'AppPackageInfo',
  extractTsTypeFields('AppPackageInfo'),
  extractJavaRecordFields(`${dtoRoot}/AppPackageInfoResponse.java`, 'AppPackageInfoResponse'),
);
assertSameFields(
  'UpdateUserStorageQuotaPayload',
  extractTsTypeFields('UpdateUserStorageQuotaPayload'),
  extractJavaRecordFields(`${dtoRoot}/AdminUpdateUserQuotaRequest.java`, 'AdminUpdateUserQuotaRequest'),
);

const pageQueryFields = extractTsTypeFields('AdminCloudOperationPageQuery');
const shareQueryFields = [...extractTsTypeFields('AdminCloudShareLinksQuery'), ...pageQueryFields];
const trashQueryFields = [...extractTsTypeFields('AdminCloudTrashNodesQuery'), ...pageQueryFields];
const storageUsersQueryFields = pageQueryFields;

assertSameFields(
  'AdminCloudShareLinksQuery',
  shareQueryFields,
  extractRequestParamsForMethod(operationsController, 'listShareLinks'),
);
assertSameFields(
  'AdminCloudTrashNodesQuery',
  trashQueryFields,
  extractRequestParamsForMethod(operationsController, 'listTrashNodes'),
);
assertSameFields(
  'AdminCloudStorageUsersQuery',
  storageUsersQueryFields,
  extractRequestParamsForMethod(operationsController, 'listStorageUsers'),
);

for (const contract of cloudConsoleEndpointContracts) {
  assertCloudConsoleEndpointContract(contract);
}

assertControllerBasePath(appPackageController, '/api/app-package');
assertControllerMethodMapping(appPackageController, 'downloadCurrentPackage', /@GetMapping\("\/download\/current"\)/);
assert.match(
  driveSharedSource,
  /APP_DOWNLOAD_PUBLIC_PATH = '\/api\/app-package\/download\/current'/,
  'cloud console APK download constant must stay pinned to CloudStorageApi public package download',
);

console.log('[OK] sysManage CloudStorageApi contracts verified');
