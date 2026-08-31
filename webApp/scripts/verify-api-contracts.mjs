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

function assertApiFunctionUsesPath(functionName, pathNeedle) {
  assert.match(
    extractApiFunctionBody(functionName),
    new RegExp(escapeRegExp(pathNeedle)),
    `${functionName} must stay pinned to ${pathNeedle}`,
  );
}

function sortedUnique(fields) {
  return [...new Set(fields)].sort();
}

function assertSameFields(label, actualFields, expectedFields) {
  assert.deepEqual(sortedUnique(actualFields), sortedUnique(expectedFields), `${label} fields must match`);
}

const dtoRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/dto';
const storageController = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/StorageNodeController.java';
const shareController = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/ShareLinkController.java';

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
  'UpdateProfilePayload',
  extractTsTypeFields('UpdateProfilePayload'),
  extractJavaRecordFields(`${dtoRoot}/UpdateProfileRequest.java`, 'UpdateProfileRequest'),
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

assertApiFunctionUsesPath('fetchCurrentUser', '/api/cloud-profile/me');
assertApiFunctionUsesPath('uploadCurrentUserAvatar', '/api/cloud-profile/avatar');
assertApiFunctionUsesPath('uploadCurrentUserHomeBackground', '/api/cloud-profile/background');
assertApiFunctionUsesPath('clearCurrentUserHomeBackground', '/api/cloud-profile/background');
assertApiFunctionUsesPath('fetchDriveOverview', '/api/storage/overview');
assertApiFunctionUsesPath('fetchUsageHistory', '/api/storage/usage-history');
assertApiFunctionUsesPath('fetchStorageNodes', '/api/storage/nodes');
assertApiFunctionUsesPath('fetchStorageFolders', '/api/storage/folders');
assertApiFunctionUsesPath('fetchTrashNodes', '/api/storage/trash');
assertApiFunctionUsesPath('createFolder', '/api/storage/folders');
assertApiFunctionUsesPath('uploadStorageFile', '/api/storage/files');
assertApiFunctionUsesPath('createMultipartUpload', '/api/storage/files/multipart');
assertApiFunctionUsesPath('fetchMultipartUploadStatus', '/api/storage/files/multipart/');
assertApiFunctionUsesPath('uploadMultipartPart', '/api/storage/files/multipart/');
assertApiFunctionUsesPath('completeMultipartUpload', '/api/storage/files/multipart/');
assertApiFunctionUsesPath('abortMultipartUpload', '/api/storage/files/multipart/');
assertApiFunctionUsesPath('downloadStorageFile', '/api/storage/files/');
assertApiFunctionUsesPath('fetchStorageFileAccessUrl', '/api/storage/files/');
assertApiFunctionUsesPath('downloadStorageArchive', '/api/storage/nodes/archive');
assertApiFunctionUsesPath('createShareLink', '/api/share-links');
assertApiFunctionUsesPath('fetchMyShareLinks', '/api/share-links/my');
assertApiFunctionUsesPath('revokeShareLink', '/api/share-links/');
assertApiFunctionUsesPath('fetchPublicShareStatus', '/api/public/share-links/');
assertApiFunctionUsesPath('verifySharePassword', '/api/public/share-links/');
assertApiFunctionUsesPath('fetchShareDetail', '/api/share-links/');
assertApiFunctionUsesPath('saveShareToDrive', '/api/share-links/');
assertApiFunctionUsesPath('fetchShareFileAccessUrl', '/api/share-links/');
assertApiFunctionUsesPath('downloadShareFile', '/api/share-links/');
assertApiFunctionUsesPath('downloadShareArchive', '/api/share-links/');
assertApiFunctionUsesPath('renameStorageNode', '/api/storage/nodes/');
assertApiFunctionUsesPath('moveStorageNode', '/api/storage/nodes/');
assertApiFunctionUsesPath('moveStorageNodes', '/api/storage/nodes/batch/move');
assertApiFunctionUsesPath('deleteStorageNode', '/api/storage/nodes/');
assertApiFunctionUsesPath('deleteStorageNodes', '/api/storage/nodes/batch/trash');
assertApiFunctionUsesPath('restoreStorageNode', '/api/storage/trash/');
assertApiFunctionUsesPath('restoreStorageNodes', '/api/storage/trash/batch/restore');
assertApiFunctionUsesPath('permanentlyDeleteStorageNode', '/api/storage/trash/');
assertApiFunctionUsesPath('permanentlyDeleteStorageNodes', '/api/storage/trash/batch/delete');
assertApiFunctionUsesPath('fetchPublicAppPackage', '/api/app-package');

console.log('[OK] cloud web CloudStorageApi contracts verified');
