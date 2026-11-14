import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const controllerSource = readFileSync(
  new URL('../../ragExecution/src/main/java/com/alicia/cloudstorage/ragexecution/api/admin/AdminExecutionController.java', import.meta.url),
  'utf8',
);
const serviceSource = readFileSync(
  new URL('../../ragExecution/src/main/java/com/alicia/cloudstorage/ragexecution/application/AdminExecutionQueryService.java', import.meta.url),
  'utf8',
);
const apiSource = readFileSync(new URL('../src/lib/api.ts', import.meta.url), 'utf8');
const typesSource = readFileSync(new URL('../src/types.ts', import.meta.url), 'utf8');

const expectedFields = {
  Overview: [
    'totalExecutions',
    'activeExecutions',
    'attentionExecutions',
    'retryWaitExecutions',
    'retriedSteps',
    'statusCounts',
    'generatedAt',
  ],
  PageResult: ['items', 'page', 'size', 'totalItems', 'totalPages'],
  Summary: [
    'executionId',
    'ownerUserId',
    'status',
    'risk',
    'version',
    'stepCount',
    'totalAttempts',
    'maximumAttempts',
    'actionTypes',
    'expiresAt',
    'confirmedAt',
    'queuedAt',
    'startedAt',
    'finishedAt',
    'resultCode',
    'errorCode',
    'createdAt',
    'updatedAt',
  ],
  Step: [
    'stepId',
    'stepIndex',
    'actionType',
    'status',
    'attempts',
    'availableAt',
    'startedAt',
    'finishedAt',
    'errorCode',
  ],
  Event: ['sequence', 'type', 'createdAt'],
};

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function findMatching(source, openIndex, openCharacter, closeCharacter) {
  let depth = 0;
  for (let index = openIndex; index < source.length; index += 1) {
    if (source[index] === openCharacter) {
      depth += 1;
    } else if (source[index] === closeCharacter) {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }
  throw new Error(`No matching ${closeCharacter} found.`);
}

function javaRecordFields(recordName) {
  const match = new RegExp(`record\\s+${escapeRegExp(recordName)}\\s*\\(`).exec(serviceSource);
  assert.ok(match, `Backend record ${recordName} must exist.`);
  const openIndex = serviceSource.indexOf('(', match.index);
  const closeIndex = findMatching(serviceSource, openIndex, '(', ')');
  return serviceSource
    .slice(openIndex + 1, closeIndex)
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/,$/, ''))
    .filter(Boolean)
    .map((line) => {
      const field = /([A-Za-z][A-Za-z0-9_]*)$/.exec(line);
      assert.ok(field, `Unable to parse ${recordName} field: ${line}`);
      return field[1];
    });
}

function tsTypeFields(typeName) {
  const match = new RegExp(`export\\s+type\\s+${escapeRegExp(typeName)}\\s*=\\s*\\{`).exec(typesSource);
  assert.ok(match, `Frontend type ${typeName} must exist.`);
  const openIndex = typesSource.indexOf('{', match.index);
  const closeIndex = findMatching(typesSource, openIndex, '{', '}');
  return typesSource
    .slice(openIndex + 1, closeIndex)
    .split(/\r?\n/)
    .map((line) => /^\s*([A-Za-z][A-Za-z0-9_]*)\??:/.exec(line)?.[1])
    .filter(Boolean);
}

function apiFunctionBody(functionName) {
  const match = new RegExp(`export\\s+function\\s+${escapeRegExp(functionName)}\\s*\\(`).exec(apiSource);
  assert.ok(match, `Frontend API function ${functionName} must exist.`);
  const openIndex = apiSource.indexOf('{', match.index);
  const closeIndex = findMatching(apiSource, openIndex, '{', '}');
  return apiSource.slice(openIndex + 1, closeIndex);
}

assert.match(
  controllerSource,
  /@RequestMapping\("\/api\/admin\/executions"\)/,
  'Backend admin execution API must retain its dedicated route family.',
);
assert.match(controllerSource, /@GetMapping\("\/overview"\)/, 'Overview must remain GET-only.');
assert.match(controllerSource, /@GetMapping\s*\r?\n/, 'Execution list must remain GET-only.');
assert.match(controllerSource, /@GetMapping\("\/\{executionId\}"\)/, 'Execution detail must remain GET-only.');
assert.doesNotMatch(
  controllerSource,
  /@(Post|Put|Patch|Delete)Mapping\b/,
  'The observability control plane must not expose execution mutations.',
);
assert.equal(
  controllerSource.match(/@RequestHeader\(value = HttpHeaders\.AUTHORIZATION/g)?.length,
  3,
  'Every admin execution endpoint must require the authorization header contract.',
);

assert.match(serviceSource, /if \(!features\.enabled\(\)\)/, 'Admin queries must honor the feature gate.');
assert.match(
  serviceSource,
  /if \(!"RAG_ADMIN"\.equals\(principal\.ragRole\(\)\)\)/,
  'Admin queries must require the explicit RAG_ADMIN application role.',
);
assert.equal(
  serviceSource.match(/@Transactional\(readOnly = true\)/g)?.length,
  3,
  'All observability service entry points must remain read-only transactions.',
);
assert.match(
  serviceSource,
  /findByExecutionIdAndVisibilityAndSequenceNoGreaterThanOrderBySequenceNoAsc\([\s\S]*ExecutionEventVisibility\.PUBLIC/,
  'Admin detail must expose only public event metadata.',
);

for (const [recordName, fields] of Object.entries(expectedFields)) {
  assert.deepEqual(javaRecordFields(recordName), fields, `Backend ${recordName} response fields changed.`);
}

assert.deepEqual(tsTypeFields('RagExecutionOverview'), expectedFields.Overview);
assert.deepEqual(tsTypeFields('RagExecutionPage'), expectedFields.PageResult);
assert.deepEqual(tsTypeFields('RagExecutionSummary'), expectedFields.Summary);
assert.deepEqual(tsTypeFields('RagExecutionStep'), expectedFields.Step);
assert.deepEqual(tsTypeFields('RagExecutionEvent'), expectedFields.Event);
assert.deepEqual(tsTypeFields('RagExecutionDetail'), ['execution', 'steps', 'events']);

for (const privateField of [
  'conversationId',
  'assistantResponseId',
  'planId',
  'planHash',
  'idempotencyKey',
  'actionSummary',
  'payload',
  'result',
]) {
  for (const fields of Object.values(expectedFields)) {
    assert.ok(!fields.includes(privateField), `Private field ${privateField} must not enter admin responses.`);
  }
}

const apiContracts = [
  ['fetchRagExecutionOverview', '/rag-execution/api/admin/executions/overview'],
  ['fetchRagExecutions', '/rag-execution/api/admin/executions'],
  ['fetchRagExecutionDetail', '/rag-execution/api/admin/executions/'],
];
assert.equal(
  apiSource.match(/\/rag-execution\/api\/admin\/executions/g)?.length,
  apiContracts.length,
  'sysManage must expose only the three approved RAG execution read endpoints.',
);
assert.doesNotMatch(
  apiSource,
  /\/rag-execution\/api\/(?!admin\/executions)/,
  'sysManage must not call other RAG execution API families.',
);
for (const [functionName, route] of apiContracts) {
  const body = apiFunctionBody(functionName);
  assert.ok(body.includes(route), `${functionName} must use ${route}.`);
  assert.match(body, /withToken\(token\)/, `${functionName} must forward the authenticated session.`);
  assert.doesNotMatch(body, /method\s*:/, `${functionName} must use the default GET request.`);
}

console.log('RAG execution admin read-only API contracts verified.');
