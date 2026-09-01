import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const typesSource = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8');
const apiSource = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8');

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
  assert.ok(match, `${typeName} must exist in webApp/src/types.ts`);

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

function extractApiFunctionBody(functionName) {
  const functionPattern = new RegExp(`export\\s+function\\s+${escapeRegExp(functionName)}\\s*\\(`);
  const match = functionPattern.exec(apiSource);
  assert.ok(match, `${functionName} must exist in webApp/src/lib/api.ts`);

  const openIndex = apiSource.indexOf('{', match.index);
  assert.ok(openIndex > -1, `${functionName} must use a function body`);

  const closeIndex = findMatching(apiSource, openIndex, '{', '}');
  return apiSource.slice(openIndex + 1, closeIndex);
}

function extractUrlSearchParamKeysForFunction(functionName) {
  const body = extractApiFunctionBody(functionName);
  const fields = [];
  const searchSetPattern = /search\.set\('([^']+)'/g;
  let match;

  while ((match = searchSetPattern.exec(body)) !== null) {
    fields.push(match[1]);
  }

  return fields;
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

function extractNamedRequestAnnotationsForMethod(relativePath, methodName, annotationName) {
  const source = readRepoFile(relativePath);
  const methodPattern = new RegExp(`${escapeRegExp(methodName)}\\s*\\(`);
  const match = methodPattern.exec(source);
  assert.ok(match, `${methodName} must exist in ${relativePath}`);

  const openIndex = source.indexOf('(', match.index);
  const closeIndex = findMatching(source, openIndex, '(', ')');
  const parameterList = source.slice(openIndex + 1, closeIndex);
  const requestFields = [];
  const annotationPattern = new RegExp(
    `@${escapeRegExp(annotationName)}(?:\\(([^)]*)\\))?\\s+[A-Za-z0-9_.<>?]+\\s+([A-Za-z][A-Za-z0-9_]*)`,
    'g',
  );
  let annotationMatch;

  while ((annotationMatch = annotationPattern.exec(parameterList)) !== null) {
    const explicitName = annotationMatch[1]?.match(/(?:^|[,\s])(?:value\s*=\s*|name\s*=\s*)?"([^"]+)"/)?.[1];
    requestFields.push(explicitName ?? annotationMatch[2]);
  }

  return requestFields;
}

function extractJavaMethodParts(relativePath, methodName) {
  const source = readRepoFile(relativePath);
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

function extractInlineQueryParamKeysForFunction(functionName) {
  const body = extractApiFunctionBody(functionName);
  const fields = [];
  const queryParamPattern = /[?&]([A-Za-z][A-Za-z0-9_]*)=/g;
  let match;

  while ((match = queryParamPattern.exec(body)) !== null) {
    fields.push(match[1]);
  }

  return fields;
}

function extractJsonStringifyObjectFieldsForFunction(functionName) {
  const body = extractApiFunctionBody(functionName);
  const fields = [];
  let searchIndex = 0;

  while (searchIndex < body.length) {
    const stringifyIndex = body.indexOf('JSON.stringify(', searchIndex);
    if (stringifyIndex === -1) {
      break;
    }

    const openIndex = body.indexOf('(', stringifyIndex);
    const closeIndex = findMatching(body, openIndex, '(', ')');
    const argumentSource = body.slice(openIndex + 1, closeIndex);
    const fieldPattern = /(?:^|[{\s,?])([A-Za-z][A-Za-z0-9_]*)\s*:/g;
    let fieldMatch;

    while ((fieldMatch = fieldPattern.exec(argumentSource)) !== null) {
      fields.push(fieldMatch[1]);
    }

    searchIndex = closeIndex + 1;
  }

  return fields;
}

function extractFormDataAppendKeysForFunction(functionName) {
  const body = extractApiFunctionBody(functionName);
  const fields = [];
  const appendPattern = /formData\.append\(\s*['"`]([^'"`]+)['"`]/g;
  let match;

  while ((match = appendPattern.exec(body)) !== null) {
    fields.push(match[1]);
  }

  return fields;
}

function assertApiFunctionUsesPath(functionName, pathNeedle) {
  assert.match(
    extractApiFunctionBody(functionName),
    new RegExp(escapeRegExp(pathNeedle)),
    `${functionName} must stay pinned to ${pathNeedle}`,
  );
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
  assert.match(context, mappingPattern, `${methodName} must keep its route mapping`);
}

function assertApiFunctionUsesMethod(functionName, method) {
  assert.match(
    extractApiFunctionBody(functionName),
    new RegExp(`method:\\s*'${escapeRegExp(method)}'`),
    `${functionName} must use ${method}`,
  );
}

function assertApiUploadFunctionUsesPost(functionName, helperName = 'requestUploadJson') {
  assert.match(
    extractApiFunctionBody(functionName),
    new RegExp(`${escapeRegExp(helperName)}<`),
    `${functionName} must use ${helperName}`,
  );
  assert.match(apiSource, /xhr\.open\('POST', url\)/, 'webApp upload request helpers must use POST');
}

function assertApiFunctionUsesAuthToken(functionName) {
  const source = extractApiFunctionBody(functionName);
  assert.match(
    source,
    /withToken\((?:token|accessToken)\b|withTokenAndShareAccess\(token\b|request(?:Binary)?UploadJson<[\s\S]*,\s*token\b/,
    `${functionName} must send the current auth token`,
  );
}

function assertApiFunctionStringifiesPayload(functionName) {
  assert.match(
    extractApiFunctionBody(functionName),
    /body:\s*JSON\.stringify\(payload\)/,
    `${functionName} must send the typed payload as its request body`,
  );
}

function assertApiFunctionUsesShareAccessToken(functionName) {
  const source = extractApiFunctionBody(functionName);
  assert.match(
    apiSource,
    /'X-Share-Access-Token':\s*shareAccessToken/,
    'webApp share access helper must send X-Share-Access-Token',
  );
  assert.match(
    source,
    /withTokenAndShareAccess\(token,\s*shareAccessToken\b/,
    `${functionName} must send the share access token when available`,
  );
}

function assertCloudStorageApiCurrentPrincipalInterceptorContract() {
  const source = readRepoFile('CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/config/WebMvcConfig.java');
  assert.match(
    source,
    /registry\.addInterceptor\(currentPrincipalInterceptor\)[\s\S]*\.addPathPatterns\([\s\S]*"\/api\/cloud-profile\/me"[\s\S]*"\/api\/cloud-profile\/avatar"[\s\S]*"\/api\/cloud-profile\/background"[\s\S]*"\/api\/share-links\/\*\*"[\s\S]*"\/api\/storage\/\*\*"[\s\S]*\)/,
    'CloudStorageApi must protect cloud web user APIs with CurrentPrincipalInterceptor',
  );
}

function assertControllerMethodReceivesAuthorization(relativePath, methodName) {
  const { parameters, body } = extractJavaMethodParts(relativePath, methodName);
  assert.match(
    parameters,
    /@RequestHeader\s*\(\s*value\s*=\s*HttpHeaders\.AUTHORIZATION\s*,\s*required\s*=\s*false\s*\)\s+String\s+authorization/,
    `${methodName} must receive the Authorization header`,
  );
  assert.match(body, /\bauthorization\b/, `${methodName} must pass Authorization to the Identity service layer`);
}

function assertControllerMethodReceivesShareAccessToken(relativePath, methodName) {
  const source = readRepoFile(relativePath);
  const { parameters, body } = extractJavaMethodParts(relativePath, methodName);
  assert.match(
    source,
    /SHARE_ACCESS_HEADER\s*=\s*"X-Share-Access-Token"/,
    `${relativePath} must keep the share access header name`,
  );
  assert.match(
    parameters,
    /@RequestHeader\s*\(\s*value\s*=\s*SHARE_ACCESS_HEADER\s*,\s*required\s*=\s*false\s*\)\s+String\s+shareAccessToken/,
    `${methodName} must receive the share access token header`,
  );
  assert.match(body, /\bshareAccessToken\b/, `${methodName} must pass the share access token to the service layer`);
}

function assertCloudWebEndpointContract(contract) {
  assertControllerBasePath(contract.controller, contract.basePath);
  assertControllerMethodMapping(contract.controller, contract.javaMethod, contract.mappingPattern);
  assertApiFunctionUsesPath(contract.tsFunction, contract.endpointNeedle);

  if (contract.requiresAuthToken) {
    assertApiFunctionUsesAuthToken(contract.tsFunction);
  }

  if (contract.requiresAuthorizationHeader) {
    assertControllerMethodReceivesAuthorization(contract.controller, contract.javaMethod);
  }

  if (contract.requiresShareAccessToken) {
    assertApiFunctionUsesShareAccessToken(contract.tsFunction);
    assertControllerMethodReceivesShareAccessToken(contract.controller, contract.javaMethod);
  }

  if (contract.httpMethod) {
    assertApiFunctionUsesMethod(contract.tsFunction, contract.httpMethod);
  }

  if (contract.uploadPost) {
    assertApiUploadFunctionUsesPost(contract.tsFunction, contract.uploadHelper);
  }
}

function sortedUnique(fields) {
  return [...new Set(fields)].sort();
}

function assertSameFields(label, actualFields, expectedFields) {
  assert.deepEqual(sortedUnique(actualFields), sortedUnique(expectedFields), `${label} fields must match`);
}

function assertSubsetFields(label, actualFields, expectedFields) {
  const allowed = new Set(expectedFields);
  const unexpectedFields = sortedUnique(actualFields).filter((field) => !allowed.has(field));
  assert.deepEqual(unexpectedFields, [], `${label} fields must be declared by backend DTO`);
}

function assertIncludesFields(label, actualFields, requiredFields) {
  const actual = new Set(actualFields);
  const missingFields = requiredFields.filter((field) => !actual.has(field));
  assert.deepEqual(missingFields, [], `${label} fields must be sent by the frontend`);
}

const dtoRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/dto';
const controllerRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller';
const cloudProfileController = `${controllerRoot}/CloudProfileController.java`;
const storageController = `${controllerRoot}/StorageNodeController.java`;
const shareController = `${controllerRoot}/ShareLinkController.java`;
const publicShareController = `${controllerRoot}/PublicShareLinkController.java`;
const appPackageController = `${controllerRoot}/AppPackageController.java`;
const identityDtoRoot = 'identityApi/src/main/java/com/alicia/cloudstorage/identity/dto';
const identityControllerRoot = 'identityApi/src/main/java/com/alicia/cloudstorage/identity/controller';
const identityAuthController = `${identityControllerRoot}/IdentityAuthController.java`;

const cloudWebEndpointContracts = [
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'me',
    mappingPattern: /@GetMapping\("\/me"\)/,
    tsFunction: 'fetchCurrentUser',
    endpointNeedle: '/api/cloud-profile/me',
    requiresAuthToken: true,
  },
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'uploadAvatar',
    mappingPattern: /@PostMapping\(value = "\/avatar", consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
    tsFunction: 'uploadCurrentUserAvatar',
    endpointNeedle: '/api/cloud-profile/avatar',
    uploadPost: true,
    requiresAuthToken: true,
  },
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'uploadHomeBackground',
    mappingPattern: /@PostMapping\(value = "\/background", consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
    tsFunction: 'uploadCurrentUserHomeBackground',
    endpointNeedle: '/api/cloud-profile/background',
    uploadPost: true,
    requiresAuthToken: true,
  },
  {
    controller: cloudProfileController,
    basePath: '/api/cloud-profile',
    javaMethod: 'clearHomeBackground',
    mappingPattern: /@DeleteMapping\("\/background"\)/,
    tsFunction: 'clearCurrentUserHomeBackground',
    endpointNeedle: '/api/cloud-profile/background',
    httpMethod: 'DELETE',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'getOverview',
    mappingPattern: /@GetMapping\("\/overview"\)/,
    tsFunction: 'fetchDriveOverview',
    endpointNeedle: '/api/storage/overview',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'getUsageHistory',
    mappingPattern: /@GetMapping\("\/usage-history"\)/,
    tsFunction: 'fetchUsageHistory',
    endpointNeedle: '/api/storage/usage-history',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'listNodes',
    mappingPattern: /@GetMapping\("\/nodes"\)/,
    tsFunction: 'fetchStorageNodes',
    endpointNeedle: '/api/storage/nodes',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'listFolders',
    mappingPattern: /@GetMapping\("\/folders"\)/,
    tsFunction: 'fetchStorageFolders',
    endpointNeedle: '/api/storage/folders',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'listTrashNodes',
    mappingPattern: /@GetMapping\("\/trash"\)/,
    tsFunction: 'fetchTrashNodes',
    endpointNeedle: '/api/storage/trash',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'createFolder',
    mappingPattern: /@PostMapping\("\/folders"\)/,
    tsFunction: 'createFolder',
    endpointNeedle: '/api/storage/folders',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'uploadFile',
    mappingPattern: /@PostMapping\(value = "\/files", consumes = MediaType\.MULTIPART_FORM_DATA_VALUE\)/,
    tsFunction: 'uploadStorageFile',
    endpointNeedle: '/api/storage/files',
    uploadPost: true,
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'createMultipartUpload',
    mappingPattern: /@PostMapping\("\/files\/multipart"\)/,
    tsFunction: 'createMultipartUpload',
    endpointNeedle: '/api/storage/files/multipart',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'getMultipartUploadStatus',
    mappingPattern: /@GetMapping\("\/files\/multipart\/\{uploadToken\}"\)/,
    tsFunction: 'fetchMultipartUploadStatus',
    endpointNeedle: '/api/storage/files/multipart/',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'uploadMultipartPart',
    mappingPattern: /@PostMapping\(value = "\/files\/multipart\/\{uploadToken\}\/parts\/\{partNumber\}", consumes = MediaType\.APPLICATION_OCTET_STREAM_VALUE\)/,
    tsFunction: 'uploadMultipartPart',
    endpointNeedle: '/api/storage/files/multipart/',
    uploadPost: true,
    uploadHelper: 'requestBinaryUploadJson',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'completeMultipartUpload',
    mappingPattern: /@PostMapping\("\/files\/multipart\/\{uploadToken\}\/complete"\)/,
    tsFunction: 'completeMultipartUpload',
    endpointNeedle: '/api/storage/files/multipart/',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'abortMultipartUpload',
    mappingPattern: /@DeleteMapping\("\/files\/multipart\/\{uploadToken\}"\)/,
    tsFunction: 'abortMultipartUpload',
    endpointNeedle: '/api/storage/files/multipart/',
    httpMethod: 'DELETE',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'downloadFile',
    mappingPattern: /@GetMapping\("\/files\/\{fileId\}\/download"\)/,
    tsFunction: 'downloadStorageFile',
    endpointNeedle: '/api/storage/files/',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'getFileAccessUrl',
    mappingPattern: /@GetMapping\("\/files\/\{fileId\}\/access-url"\)/,
    tsFunction: 'fetchStorageFileAccessUrl',
    endpointNeedle: '/api/storage/files/',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'downloadArchive',
    mappingPattern: /@PostMapping\("\/nodes\/archive"\)/,
    tsFunction: 'downloadStorageArchive',
    endpointNeedle: '/api/storage/nodes/archive',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'renameNode',
    mappingPattern: /@PutMapping\("\/nodes\/\{nodeId\}\/rename"\)/,
    tsFunction: 'renameStorageNode',
    endpointNeedle: '/api/storage/nodes/',
    httpMethod: 'PUT',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'moveNode',
    mappingPattern: /@PutMapping\("\/nodes\/\{nodeId\}\/move"\)/,
    tsFunction: 'moveStorageNode',
    endpointNeedle: '/api/storage/nodes/',
    httpMethod: 'PUT',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'moveNodes',
    mappingPattern: /@PutMapping\("\/nodes\/batch\/move"\)/,
    tsFunction: 'moveStorageNodes',
    endpointNeedle: '/api/storage/nodes/batch/move',
    httpMethod: 'PUT',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'moveNodeToTrash',
    mappingPattern: /@DeleteMapping\("\/nodes\/\{nodeId\}"\)/,
    tsFunction: 'deleteStorageNode',
    endpointNeedle: '/api/storage/nodes/',
    httpMethod: 'DELETE',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'moveNodesToTrash',
    mappingPattern: /@PostMapping\("\/nodes\/batch\/trash"\)/,
    tsFunction: 'deleteStorageNodes',
    endpointNeedle: '/api/storage/nodes/batch/trash',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'restoreNode',
    mappingPattern: /@PostMapping\("\/trash\/\{nodeId\}\/restore"\)/,
    tsFunction: 'restoreStorageNode',
    endpointNeedle: '/api/storage/trash/',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'restoreNodes',
    mappingPattern: /@PostMapping\("\/trash\/batch\/restore"\)/,
    tsFunction: 'restoreStorageNodes',
    endpointNeedle: '/api/storage/trash/batch/restore',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'permanentlyDeleteNode',
    mappingPattern: /@DeleteMapping\("\/trash\/\{nodeId\}"\)/,
    tsFunction: 'permanentlyDeleteStorageNode',
    endpointNeedle: '/api/storage/trash/',
    httpMethod: 'DELETE',
    requiresAuthToken: true,
  },
  {
    controller: storageController,
    basePath: '/api/storage',
    javaMethod: 'permanentlyDeleteNodes',
    mappingPattern: /@PostMapping\("\/trash\/batch\/delete"\)/,
    tsFunction: 'permanentlyDeleteStorageNodes',
    endpointNeedle: '/api/storage/trash/batch/delete',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'createShareLink',
    mappingPattern: /@PostMapping\b/,
    tsFunction: 'createShareLink',
    endpointNeedle: '/api/share-links',
    httpMethod: 'POST',
    requiresAuthToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'listMyShareLinks',
    mappingPattern: /@GetMapping\("\/my"\)/,
    tsFunction: 'fetchMyShareLinks',
    endpointNeedle: '/api/share-links/my',
    requiresAuthToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'revokeShareLink',
    mappingPattern: /@DeleteMapping\("\/\{shareId\}"\)/,
    tsFunction: 'revokeShareLink',
    endpointNeedle: '/api/share-links/',
    httpMethod: 'DELETE',
    requiresAuthToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'getShareDetail',
    mappingPattern: /@GetMapping\("\/\{shareCode\}\/detail"\)/,
    tsFunction: 'fetchShareDetail',
    endpointNeedle: '/api/share-links/',
    requiresAuthToken: true,
    requiresShareAccessToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'saveShare',
    mappingPattern: /@PostMapping\("\/\{shareCode\}\/save"\)/,
    tsFunction: 'saveShareToDrive',
    endpointNeedle: '/api/share-links/',
    httpMethod: 'POST',
    requiresAuthToken: true,
    requiresShareAccessToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'getShareFileAccessUrl',
    mappingPattern: /@GetMapping\("\/\{shareCode\}\/files\/\{fileId\}\/access-url"\)/,
    tsFunction: 'fetchShareFileAccessUrl',
    endpointNeedle: '/api/share-links/',
    requiresAuthToken: true,
    requiresShareAccessToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'downloadShareFile',
    mappingPattern: /@GetMapping\("\/\{shareCode\}\/files\/\{fileId\}\/download"\)/,
    tsFunction: 'downloadShareFile',
    endpointNeedle: '/api/share-links/',
    requiresAuthToken: true,
    requiresShareAccessToken: true,
  },
  {
    controller: shareController,
    basePath: '/api/share-links',
    javaMethod: 'downloadShareArchive',
    mappingPattern: /@PostMapping\("\/\{shareCode\}\/nodes\/archive"\)/,
    tsFunction: 'downloadShareArchive',
    endpointNeedle: '/api/share-links/',
    httpMethod: 'POST',
    requiresAuthToken: true,
    requiresShareAccessToken: true,
  },
  {
    controller: publicShareController,
    basePath: '/api/public/share-links',
    javaMethod: 'getPublicStatus',
    mappingPattern: /@GetMapping\("\/\{shareCode\}\/status"\)/,
    tsFunction: 'fetchPublicShareStatus',
    endpointNeedle: '/api/public/share-links/',
  },
  {
    controller: publicShareController,
    basePath: '/api/public/share-links',
    javaMethod: 'verifyPassword',
    mappingPattern: /@PostMapping\("\/\{shareCode\}\/verify-password"\)/,
    tsFunction: 'verifySharePassword',
    endpointNeedle: '/api/public/share-links/',
    httpMethod: 'POST',
  },
  {
    controller: appPackageController,
    basePath: '/api/app-package',
    javaMethod: 'getCurrentPackageInfo',
    mappingPattern: /@GetMapping\b/,
    tsFunction: 'fetchPublicAppPackage',
    endpointNeedle: '/api/app-package',
  },
];

const cloudWebIdentityEndpointContracts = [
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
    javaMethod: 'logout',
    mappingPattern: /@PostMapping\("\/logout"\)/,
    tsFunction: 'logoutAuthToken',
    endpointNeedle: '/api/identity/auth/logout',
    httpMethod: 'POST',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
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
    javaMethod: 'updateProfile',
    mappingPattern: /@PutMapping\("\/profile"\)/,
    tsFunction: 'updateProfile',
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
    tsFunction: 'changePassword',
    endpointNeedle: '/api/identity/auth/password',
    httpMethod: 'PUT',
    requiresAuthToken: true,
    requiresAuthorizationHeader: true,
  },
];

assertSameFields(
  'User',
  extractTsTypeFields('User'),
  extractJavaRecordFields(`${dtoRoot}/UserProfileResponse.java`, 'UserProfileResponse'),
);
assertSameFields(
  'DriveOverview',
  extractTsTypeFields('DriveOverview'),
  extractJavaRecordFields(`${dtoRoot}/DriveOverviewResponse.java`, 'DriveOverviewResponse'),
);
assertSameFields(
  'UsageHistoryPoint',
  extractTsTypeFields('UsageHistoryPoint'),
  extractJavaRecordFields(`${dtoRoot}/UsageHistoryPointResponse.java`, 'UsageHistoryPointResponse'),
);
assertSameFields(
  'StorageNode',
  extractTsTypeFields('StorageNode'),
  extractJavaRecordFields(`${dtoRoot}/StorageNodeSummaryResponse.java`, 'StorageNodeSummaryResponse'),
);
assertSameFields(
  'StorageNodePage',
  extractTsTypeFields('StorageNodePage'),
  extractJavaRecordFields(`${dtoRoot}/PageResponse.java`, 'PageResponse'),
);
assertSameFields(
  'SignedUrlResponse',
  extractTsTypeFields('SignedUrlResponse'),
  extractJavaRecordFields(`${dtoRoot}/SignedUrlResponse.java`, 'SignedUrlResponse'),
);
assertSameFields(
  'MultipartUploadPart',
  extractTsTypeFields('MultipartUploadPart'),
  extractJavaRecordFields(`${dtoRoot}/MultipartUploadPartResponse.java`, 'MultipartUploadPartResponse'),
);
assertSameFields(
  'MultipartUploadStatus',
  extractTsTypeFields('MultipartUploadStatus'),
  extractJavaRecordFields(`${dtoRoot}/MultipartUploadStatusResponse.java`, 'MultipartUploadStatusResponse'),
);
assertSameFields(
  'ApiMessageResponse',
  extractTsTypeFields('ApiMessageResponse'),
  extractJavaRecordFields(`${dtoRoot}/ApiMessageResponse.java`, 'ApiMessageResponse'),
);
assertSameFields(
  'AppPackageInfo',
  extractTsTypeFields('AppPackageInfo'),
  extractJavaRecordFields(`${dtoRoot}/AppPackageInfoResponse.java`, 'AppPackageInfoResponse'),
);
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
  'UpdateProfilePayload',
  extractTsTypeFields('UpdateProfilePayload'),
  extractJavaRecordFields(`${identityDtoRoot}/UpdateIdentityProfileRequest.java`, 'UpdateIdentityProfileRequest'),
);
assertSameFields(
  'ChangePasswordPayload',
  extractTsTypeFields('ChangePasswordPayload'),
  extractJavaRecordFields(`${identityDtoRoot}/ChangePasswordRequest.java`, 'ChangePasswordRequest'),
);
assertSameFields(
  'CreateFolderPayload',
  extractTsTypeFields('CreateFolderPayload'),
  extractJavaRecordFields(`${dtoRoot}/CreateFolderRequest.java`, 'CreateFolderRequest'),
);
assertSameFields(
  'RenameNodePayload',
  extractTsTypeFields('RenameNodePayload'),
  extractJavaRecordFields(`${dtoRoot}/RenameNodeRequest.java`, 'RenameNodeRequest'),
);
assertSameFields(
  'MoveNodePayload',
  extractTsTypeFields('MoveNodePayload'),
  extractJavaRecordFields(`${dtoRoot}/MoveNodeRequest.java`, 'MoveNodeRequest'),
);
assertSameFields(
  'BatchNodePayload',
  extractTsTypeFields('BatchNodePayload'),
  extractJavaRecordFields(`${dtoRoot}/BatchNodeRequest.java`, 'BatchNodeRequest'),
);
assertSameFields(
  'BatchMoveNodePayload',
  [...extractTsTypeFields('BatchNodePayload'), ...extractTsTypeFields('BatchMoveNodePayload')],
  extractJavaRecordFields(`${dtoRoot}/BatchMoveNodeRequest.java`, 'BatchMoveNodeRequest'),
);
assertSameFields(
  'CreateMultipartUploadPayload',
  extractTsTypeFields('CreateMultipartUploadPayload'),
  extractJavaRecordFields(`${dtoRoot}/CreateMultipartUploadRequest.java`, 'CreateMultipartUploadRequest'),
);
assertSameFields(
  'CreateShareLinkPayload',
  extractTsTypeFields('CreateShareLinkPayload'),
  extractJavaRecordFields(`${dtoRoot}/CreateShareLinkRequest.java`, 'CreateShareLinkRequest'),
);
assertSameFields(
  'ShareLinkSummary',
  extractTsTypeFields('ShareLinkSummary'),
  extractJavaRecordFields(`${dtoRoot}/ShareLinkSummaryResponse.java`, 'ShareLinkSummaryResponse'),
);
assertSameFields(
  'ShareLinkStatus',
  extractTsTypeFields('ShareLinkStatus'),
  extractJavaRecordFields(`${dtoRoot}/ShareLinkStatusResponse.java`, 'ShareLinkStatusResponse'),
);
assertSameFields(
  'VerifySharePasswordPayload',
  extractTsTypeFields('VerifySharePasswordPayload'),
  extractJavaRecordFields(`${dtoRoot}/VerifySharePasswordRequest.java`, 'VerifySharePasswordRequest'),
);
assertSameFields(
  'VerifySharePasswordResponse',
  extractTsTypeFields('VerifySharePasswordResponse'),
  extractJavaRecordFields(`${dtoRoot}/VerifySharePasswordResponse.java`, 'VerifySharePasswordResponse'),
);
assertSameFields(
  'ShareLinkDetail',
  extractTsTypeFields('ShareLinkDetail'),
  extractJavaRecordFields(`${dtoRoot}/ShareLinkDetailResponse.java`, 'ShareLinkDetailResponse'),
);
assertSameFields(
  'SaveShareLinkPayload',
  extractTsTypeFields('SaveShareLinkPayload'),
  extractJavaRecordFields(`${dtoRoot}/SaveShareLinkRequest.java`, 'SaveShareLinkRequest'),
);
assertSameFields(
  'refreshAuthSession request body',
  extractJsonStringifyObjectFieldsForFunction('refreshAuthSession'),
  extractJavaRecordFields(`${identityDtoRoot}/IdentityRefreshTokenRequest.java`, 'IdentityRefreshTokenRequest'),
);

const logoutAuthTokenRequestFields = extractJsonStringifyObjectFieldsForFunction('logoutAuthToken');
assertSubsetFields(
  'logoutAuthToken request body',
  logoutAuthTokenRequestFields,
  extractJavaRecordFields(`${identityDtoRoot}/IdentityLogoutRequest.java`, 'IdentityLogoutRequest'),
);
assertIncludesFields('logoutAuthToken request body', logoutAuthTokenRequestFields, ['refreshToken']);

assertSameFields(
  'uploadCurrentUserAvatar multipart fields',
  extractFormDataAppendKeysForFunction('uploadCurrentUserAvatar'),
  extractNamedRequestAnnotationsForMethod(cloudProfileController, 'uploadAvatar', 'RequestPart'),
);
assertSameFields(
  'uploadCurrentUserHomeBackground multipart fields',
  extractFormDataAppendKeysForFunction('uploadCurrentUserHomeBackground'),
  extractNamedRequestAnnotationsForMethod(cloudProfileController, 'uploadHomeBackground', 'RequestPart'),
);
assertSameFields(
  'uploadStorageFile multipart fields',
  extractFormDataAppendKeysForFunction('uploadStorageFile'),
  [
    ...extractNamedRequestAnnotationsForMethod(storageController, 'uploadFile', 'RequestParam'),
    ...extractNamedRequestAnnotationsForMethod(storageController, 'uploadFile', 'RequestPart'),
  ],
);

assertSameFields(
  'fetchStorageNodes query parameters',
  extractUrlSearchParamKeysForFunction('fetchStorageNodes'),
  extractRequestParamsForMethod(storageController, 'listNodes'),
);
assertSameFields(
  'fetchTrashNodes query parameters',
  extractUrlSearchParamKeysForFunction('fetchTrashNodes'),
  extractRequestParamsForMethod(storageController, 'listTrashNodes'),
);
assertSameFields(
  'fetchUsageHistory query parameters',
  extractUrlSearchParamKeysForFunction('fetchUsageHistory'),
  extractRequestParamsForMethod(storageController, 'getUsageHistory'),
);
assertSameFields(
  'fetchStorageFileAccessUrl query parameters',
  extractUrlSearchParamKeysForFunction('fetchStorageFileAccessUrl'),
  extractRequestParamsForMethod(storageController, 'getFileAccessUrl'),
);
assertSameFields(
  'fetchShareFileAccessUrl query parameters',
  extractUrlSearchParamKeysForFunction('fetchShareFileAccessUrl'),
  extractRequestParamsForMethod(shareController, 'getShareFileAccessUrl'),
);
assertSameFields(
  'fetchIdentitySessions query parameters',
  [
    ...extractUrlSearchParamKeysForFunction('fetchIdentitySessions'),
    ...extractInlineQueryParamKeysForFunction('fetchIdentitySessions'),
  ],
  extractRequestParamsForMethod(identityAuthController, 'listSessions'),
);

for (const functionName of [
  'updateProfile',
  'changePassword',
  'createFolder',
  'createMultipartUpload',
  'downloadStorageArchive',
  'createShareLink',
  'verifySharePassword',
  'saveShareToDrive',
  'downloadShareArchive',
  'renameStorageNode',
  'moveStorageNode',
  'moveStorageNodes',
  'deleteStorageNodes',
  'restoreStorageNodes',
  'permanentlyDeleteStorageNodes',
]) {
  assertApiFunctionStringifiesPayload(functionName);
}

for (const contract of cloudWebEndpointContracts) {
  assertCloudWebEndpointContract(contract);
}

for (const contract of cloudWebIdentityEndpointContracts) {
  assertCloudWebEndpointContract(contract);
}

assertCloudStorageApiCurrentPrincipalInterceptorContract();

console.log('[OK] cloud web CloudStorageApi and IdentityApi contracts verified');
