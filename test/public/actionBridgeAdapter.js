(function initActionBridgeAdapter(root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (root) {
    root.AliciaActionBridgeAdapter = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : null, function buildAdapter() {
  const allowedMethods = new Set(["GET", "POST", "PUT", "PATCH", "DELETE"]);

  function buildPreview(draft, contract) {
    const errors = [];
    const warnings = [];
    const safeDraft = draft && typeof draft === "object" ? draft : {};
    const actionType = stringValue(safeDraft.actionType);
    const definition = contract?.actions?.[actionType];

    if (!actionType) {
      errors.push("缺少 actionType。");
    }
    if (!definition) {
      errors.push("当前 actionType 不在本地动作桥接 allowlist 中。");
    }
    if (definition?.enabled === false) {
      errors.push("当前动作在动作桥接配置中已禁用。");
    }

    validateMethod(safeDraft, definition, errors);
    validatePath(safeDraft, definition, errors);
    validateActionState(safeDraft, definition, errors, warnings);
    validateTargetCandidate(safeDraft, errors);
    validateBatchBody(safeDraft, errors);
    validateRequiredClientFields(safeDraft, warnings);

    const request = errors.length === 0 ? buildRequest(safeDraft) : null;
    return {
      valid: errors.length === 0,
      submitAllowed: errors.length === 0 && safeDraft.status === "backend_action_ready" && safeDraft.executableByBackend === true,
      status: safeDraft.status || "missing_draft",
      actionType,
      errors,
      warnings,
      request
    };
  }

  function validateMethod(draft, definition, errors) {
    const method = stringValue(draft.method).toUpperCase();
    if (!method) {
      errors.push("缺少请求 method。");
      return;
    }
    if (!allowedMethods.has(method)) {
      errors.push("请求 method 不在客户端 allowlist 中。");
    }
    if (definition?.method && method !== stringValue(definition.method).toUpperCase()) {
      errors.push("请求 method 与动作桥接契约不一致。");
    }
  }

  function validatePath(draft, definition, errors) {
    const path = stringValue(draft.path);
    if (!path) {
      errors.push("缺少请求 path。");
      return;
    }
    if (!path.startsWith("/api/") || path.includes("://") || path.includes("//") || path.includes("..") || path.includes("\\")) {
      errors.push("请求 path 未通过本地安全校验。");
    }
    if (definition?.path_template && draft.pathTemplate !== definition.path_template) {
      errors.push("pathTemplate 与动作桥接契约不一致。");
    }
  }

  function validateActionState(draft, definition, errors, warnings) {
    if (!draft.confirmedByUser) {
      errors.push("用户确认标记不存在，不能提交。");
    }
    if (definition?.next_action && draft.nextAction !== definition.next_action) {
      errors.push("nextAction 与动作桥接契约不一致。");
    }
    if (definition?.status && draft.status !== definition.status) {
      errors.push("status 与动作桥接契约不一致。");
    }
    if (draft.authorizationRequired !== true) {
      warnings.push("当前草稿未声明需要用户授权，正式提交前需复核。");
    }
    if (draft.status === "client_action_required") {
      warnings.push("该草稿需要客户端补充材料，不能直接提交 JSON 请求。");
    }
  }

  function validateTargetCandidate(draft, errors) {
    if (Array.isArray(draft.body?.nodeIds) && draft.body.nodeIds.length > 0) {
      return;
    }
    const nodeId = draft.targetCandidate?.nodeId;
    if (nodeId === null || nodeId === undefined || nodeId === "") {
      errors.push("缺少 targetCandidate.nodeId。");
    }
  }

  function validateBatchBody(draft, errors) {
    if (!draft.pathTemplate || !draft.pathTemplate.includes("/batch/")) {
      return;
    }
    const nodeIds = draft.body?.nodeIds;
    if (!Array.isArray(nodeIds) || nodeIds.length === 0) {
      errors.push("批量请求缺少 body.nodeIds。");
      return;
    }
    if (nodeIds.some((nodeId) => !Number.isInteger(Number(nodeId)) || Number(nodeId) <= 0)) {
      errors.push("批量请求 body.nodeIds 包含非法值。");
    }
    if (draft.pathTemplate.includes("/batch/move")) {
      const parentId = draft.body?.parentId;
      if (parentId !== null && parentId !== undefined && parentId !== "" && (!Number.isInteger(Number(parentId)) || Number(parentId) <= 0)) {
        errors.push("批量移动 body.parentId 包含非法值。");
      }
    }
  }

  function validateRequiredClientFields(draft, warnings) {
    const fields = Array.isArray(draft.requiredClientFields) ? draft.requiredClientFields : [];
    if (fields.length > 0) {
      warnings.push(`仍需客户端补充：${fields.join("、")}。`);
    }
  }

  function buildRequest(draft) {
    return {
      method: stringValue(draft.method).toUpperCase(),
      url: withQueryString(stringValue(draft.path), draft.queryParameters || {}),
      contentType: stringValue(draft.contentType),
      body: draft.body || {}
    };
  }

  function withQueryString(path, queryParameters) {
    const entries = Object.entries(queryParameters)
      .filter(([, value]) => value !== null && value !== undefined && value !== "")
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
    return entries.length === 0 ? path : `${path}?${entries.join("&")}`;
  }

  function stringValue(value) {
    return value === null || value === undefined ? "" : String(value).trim();
  }

  return {
    buildPreview
  };
});
