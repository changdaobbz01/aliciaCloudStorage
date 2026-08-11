const assert = require("node:assert/strict");
const { buildPreview } = require("./public/actionBridgeAdapter");

const contract = {
  actions: {
    delete: {
      enabled: true,
      status: "backend_action_ready",
      next_action: "handoff_to_backend",
      method: "DELETE",
      path_template: "/api/storage/nodes/{nodeId}"
    },
    upload_target: {
      enabled: true,
      status: "client_action_required",
      next_action: "handoff_to_client_upload",
      method: "POST",
      path_template: "/api/storage/files"
    },
    "collection.trash_by_name_contains": {
      enabled: true,
      status: "backend_action_ready",
      next_action: "handoff_to_backend",
      method: "POST",
      path_template: "/api/storage/nodes/batch/trash"
    }
  }
};

const validDeleteDraft = {
  status: "backend_action_ready",
  actionType: "delete",
  nextAction: "handoff_to_backend",
  confirmedByUser: true,
  executableByBackend: true,
  authorizationRequired: true,
  method: "DELETE",
  pathTemplate: "/api/storage/nodes/{nodeId}",
  path: "/api/storage/nodes/102",
  contentType: "",
  queryParameters: {},
  body: {},
  requiredClientFields: [],
  targetCandidate: {
    nodeId: 102,
    name: "临时截图.png"
  }
};

const validPreview = buildPreview(validDeleteDraft, contract);
assert.equal(validPreview.valid, true);
assert.equal(validPreview.submitAllowed, true);
assert.equal(validPreview.request.method, "DELETE");
assert.equal(validPreview.request.url, "/api/storage/nodes/102");

const unsafePathPreview = buildPreview({
  ...validDeleteDraft,
  path: "https://example.com/api/storage/nodes/102"
}, contract);
assert.equal(unsafePathPreview.valid, false);
assert.match(unsafePathPreview.errors.join(" "), /path/);

const missingCandidatePreview = buildPreview({
  ...validDeleteDraft,
  targetCandidate: {}
}, contract);
assert.equal(missingCandidatePreview.valid, false);
assert.match(missingCandidatePreview.errors.join(" "), /nodeId/);

const uploadPreview = buildPreview({
  status: "client_action_required",
  actionType: "upload_target",
  nextAction: "handoff_to_client_upload",
  confirmedByUser: true,
  executableByBackend: false,
  authorizationRequired: true,
  method: "POST",
  pathTemplate: "/api/storage/files",
  path: "/api/storage/files",
  contentType: "multipart/form-data",
  queryParameters: { parentId: 501 },
  body: {},
  requiredClientFields: ["file"],
  targetCandidate: { nodeId: 501, name: "项目资料" }
}, contract);
assert.equal(uploadPreview.valid, true);
assert.equal(uploadPreview.submitAllowed, false);
assert.equal(uploadPreview.request.url, "/api/storage/files?parentId=501");

const batchTrashPreview = buildPreview({
  status: "backend_action_ready",
  actionType: "collection.trash_by_name_contains",
  nextAction: "handoff_to_backend",
  confirmedByUser: true,
  executableByBackend: true,
  authorizationRequired: true,
  method: "POST",
  pathTemplate: "/api/storage/nodes/batch/trash",
  path: "/api/storage/nodes/batch/trash",
  contentType: "application/json",
  queryParameters: {},
  body: { nodeIds: [801, 802] },
  requiredClientFields: [],
  targetCandidate: null
}, contract);
assert.equal(batchTrashPreview.valid, true);
assert.equal(batchTrashPreview.submitAllowed, true);
assert.deepEqual(batchTrashPreview.request.body, { nodeIds: [801, 802] });

const emptyBatchPreview = buildPreview({
  status: "backend_action_ready",
  actionType: "collection.trash_by_name_contains",
  nextAction: "handoff_to_backend",
  confirmedByUser: true,
  executableByBackend: true,
  authorizationRequired: true,
  method: "POST",
  pathTemplate: "/api/storage/nodes/batch/trash",
  path: "/api/storage/nodes/batch/trash",
  contentType: "application/json",
  queryParameters: {},
  body: { nodeIds: [] },
  requiredClientFields: [],
  targetCandidate: null
}, contract);
assert.equal(emptyBatchPreview.valid, false);
assert.match(emptyBatchPreview.errors.join(" "), /nodeIds/);
