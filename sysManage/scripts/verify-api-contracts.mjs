import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const typesSource = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8');

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
    .map((line) => line.trim().replace(/,$/, ''))
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

const dtoRoot = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/dto';
const operationsController = 'CloudStorageApi/src/main/java/com/alicia/cloudstorage/api/controller/AdminCloudOperationsController.java';
const overviewDto = `${dtoRoot}/AdminCloudOperationsOverviewResponse.java`;

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

console.log('[OK] sysManage CloudStorageApi contracts verified');
