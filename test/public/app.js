const state = {
  currentResult: null,
  conversationId: sessionStorage.getItem("alicia-rag-conversation-id") || "",
  authorizationHeader: sessionStorage.getItem("alicia-rag-authorization") || "",
  actionBridgeContract: null,
  mobileContract: null,
  acceptanceScenarios: null,
  clientConfig: {
    startupMessage: "RAG 意图识别调试台已连接。",
    emptyPlanText: "输入一句文件操作需求，查看 RAG 返回的意图识别模板。",
    fallbackPlanText: "已生成意图识别模板。",
    examples: [],
    intentLabels: {},
    fieldLabels: {}
  }
};

const dom = {
  serviceStatus: document.querySelector("#serviceStatus"),
  schemaList: document.querySelector("#schemaList"),
  chatLog: document.querySelector("#chatLog"),
  quickRow: document.querySelector("#quickRow"),
  composer: document.querySelector("#composer"),
  messageInput: document.querySelector("#messageInput"),
  authInput: document.querySelector("#authInput"),
  saveAuthButton: document.querySelector("#saveAuthButton"),
  clearAuthButton: document.querySelector("#clearAuthButton"),
  resetConversationButton: document.querySelector("#resetConversationButton"),
  authStatus: document.querySelector("#authStatus"),
  planBody: document.querySelector("#planBody"),
  riskBadge: document.querySelector("#riskBadge")
};

init();

async function init() {
  await loadClientConfig();
  await loadActionBridgeContract();
  await loadMobileContract();
  await loadAcceptanceScenarios();
  renderSchema();
  renderQuickActions();
  renderAuthControls();
  appendMessage("assistant", state.clientConfig.startupMessage);
  await checkHealth();
  renderResult();
}

async function loadActionBridgeContract() {
  try {
    const response = await fetch("/api/assistant/contracts/action-bridge");
    if (!response.ok) throw new Error("action bridge contract failed");
    state.actionBridgeContract = await response.json();
  } catch {
    state.actionBridgeContract = null;
  }
}

async function loadMobileContract() {
  try {
    const response = await fetch("/api/assistant/contracts/mobile");
    if (!response.ok) throw new Error("mobile contract failed");
    state.mobileContract = await response.json();
  } catch {
    state.mobileContract = null;
  }
}

async function loadAcceptanceScenarios() {
  try {
    const response = await fetch("/api/assistant/contracts/acceptance-scenarios");
    if (!response.ok) throw new Error("acceptance scenarios failed");
    state.acceptanceScenarios = await response.json();
  } catch {
    state.acceptanceScenarios = null;
  }
}

async function loadClientConfig() {
  const response = await fetch("/api/config/client");
  if (!response.ok) throw new Error("client config failed");
  state.clientConfig = { ...state.clientConfig, ...(await response.json()) };
}

async function checkHealth() {
  try {
    const response = await fetch("/api/health");
    if (!response.ok) throw new Error("health failed");
    const health = await response.json();
    dom.serviceStatus.textContent = health.service || "RAG 已连接";
    dom.serviceStatus.classList.add("ok");
  } catch {
    dom.serviceStatus.textContent = "未连接";
    dom.serviceStatus.classList.remove("ok");
  }
}

function renderSchema() {
  const fields = [
    ["actionPlan", "ActionPlan staged execution plan"],
    ["intentId", "受控意图 ID"],
    ["conversation", "多轮会话状态"],
    ["entities", "用户输入中提取的业务线索"],
    ["missingSlots", "还需要用户补充的信息"],
    ["candidateBinding", "真实文件候选绑定状态"],
    ["actionDraft", "等待后端绑定的动作草稿"],
    ["backendActionDraft", "确认后可交付的后端请求草稿"],
    ["safety", "风险与确认要求"],
    ["assistantText", "DeepSeek 生成的引导文案"]
  ];

  dom.schemaList.innerHTML = fields.map(([field, description]) => `
    <div class="schema-row">
      <p class="schema-name">${escapeHtml(field)}</p>
      <p class="schema-desc">${escapeHtml(description)}</p>
    </div>
  `).join("") + renderActionBridgeSummary() + renderMobileContractSummary();
}

function renderActionBridgeSummary() {
  const actions = state.actionBridgeContract?.actions || {};
  const rows = Object.entries(actions).map(([action, definition]) => `
    <div class="schema-row">
      <p class="schema-name">${escapeHtml(action)}</p>
      <p class="schema-desc">
        ${escapeHtml(definition.method || "")}
        ${escapeHtml(definition.path_template || "")}
        ${definition.next_action ? ` / ${escapeHtml(definition.next_action)}` : ""}
      </p>
    </div>
  `).join("");
  return rows ? `
    <div class="schema-row">
      <p class="schema-name">actionBridge</p>
      <p class="schema-desc">${escapeHtml(state.actionBridgeContract?.version || "")}</p>
    </div>
    ${rows}
  ` : "";
}

function renderMobileContractSummary() {
  const actionHandlers = state.mobileContract?.actionHandlers || {};
  const automatedScenarios = state.acceptanceScenarios?.automatedScenarios || [];
  const manualScenarios = state.acceptanceScenarios?.manualScenarios || [];
  if (!state.mobileContract && !state.acceptanceScenarios) {
    return "";
  }
  return `
    <div class="schema-row">
      <p class="schema-name">mobileContract</p>
      <p class="schema-desc">
        ${escapeHtml(state.mobileContract?.version || "")}
        / handlers: ${escapeHtml(Object.keys(actionHandlers).length)}
      </p>
    </div>
    <div class="schema-row">
      <p class="schema-name">acceptance</p>
      <p class="schema-desc">
        ${escapeHtml(state.acceptanceScenarios?.version || "")}
        / auto: ${escapeHtml(automatedScenarios.length)}
        / manual: ${escapeHtml(manualScenarios.length)}
      </p>
    </div>
  `;
}

function renderQuickActions() {
  dom.quickRow.innerHTML = "";
  for (const example of state.clientConfig.examples || []) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = example;
    button.addEventListener("click", () => {
      dom.messageInput.value = example;
      dom.messageInput.focus();
    });
    dom.quickRow.appendChild(button);
  }
}

dom.composer.addEventListener("submit", async (event) => {
  event.preventDefault();
  const message = dom.messageInput.value.trim();
  if (!message) return;
  appendMessage("user", message);
  dom.messageInput.value = "";

  const headers = { "Content-Type": "application/json; charset=utf-8" };
  if (state.authorizationHeader) {
    headers.Authorization = state.authorizationHeader;
  }

  const response = await fetch("/api/assistant/plan", {
    method: "POST",
    headers,
    body: JSON.stringify({ message, conversationId: state.conversationId })
  });
  const result = await response.json();
  state.currentResult = result;
  if (result.conversation?.conversationId) {
    state.conversationId = result.conversation.conversationId;
    sessionStorage.setItem("alicia-rag-conversation-id", state.conversationId);
  }
  appendMessage("assistant", result.assistantText || state.clientConfig.fallbackPlanText);
  renderResult();
});

dom.saveAuthButton.addEventListener("click", () => {
  state.authorizationHeader = dom.authInput.value.trim();
  if (state.authorizationHeader) {
    sessionStorage.setItem("alicia-rag-authorization", state.authorizationHeader);
    appendMessage("assistant", "授权头已保存到当前浏览器会话。");
  } else {
    sessionStorage.removeItem("alicia-rag-authorization");
    appendMessage("assistant", "授权头已清空。");
  }
  renderAuthControls();
});

dom.clearAuthButton.addEventListener("click", () => {
  state.authorizationHeader = "";
  sessionStorage.removeItem("alicia-rag-authorization");
  renderAuthControls();
  appendMessage("assistant", "授权头已清空，后续请求不会透传 Authorization。");
});

dom.resetConversationButton.addEventListener("click", () => {
  state.conversationId = "";
  state.currentResult = null;
  sessionStorage.removeItem("alicia-rag-conversation-id");
  renderResult();
  appendMessage("assistant", "已切换到新会话。");
});

function appendMessage(role, text) {
  const node = document.createElement("div");
  node.className = `message ${role}`;
  node.textContent = text;
  dom.chatLog.appendChild(node);
  dom.chatLog.scrollTop = dom.chatLog.scrollHeight;
}

function renderAuthControls() {
  dom.authInput.value = state.authorizationHeader;
  dom.authStatus.textContent = state.authorizationHeader ? "已设置" : "未设置";
  dom.authStatus.className = `auth-status ${state.authorizationHeader ? "ok" : ""}`;
}

function renderResult() {
  const result = state.currentResult;
  if (!result) {
    dom.riskBadge.textContent = "none";
    dom.riskBadge.className = "risk-badge none";
    dom.planBody.innerHTML = `<p class="empty-plan">${escapeHtml(state.clientConfig.emptyPlanText)}</p>`;
    return;
  }

  const risk = result.safety?.risk || "none";
  const actionBridgePreview = buildActionBridgePreview(result.backendActionDraft);
  dom.riskBadge.textContent = risk;
  dom.riskBadge.className = `risk-badge ${risk}`;
  dom.planBody.innerHTML = `
    <div class="summary-line">
      <strong>${escapeHtml(intentLabel(result.intentId))}</strong><br />
      ${label("intentId")}：${escapeHtml(result.intentId)}<br />
      ${label("confidence")}：${escapeHtml(formatConfidence(result.confidence))}<br />
      ${label("provider")}：${escapeHtml(result.provider || "")}${result.model ? ` / ${escapeHtml(result.model)}` : ""}<br />
      ${label("nextAction")}：${escapeHtml(result.nextAction || "")}<br />
      ${label("requiresConfirmation")}：${result.safety?.requiresConfirmation ? "是" : "否"}<br />
      会话：${escapeHtml(result.conversation?.conversationId || "")}<br />
      状态：${escapeHtml(result.conversation?.status || "")}
    </div>
    <div class="template-section">
      <h3>引导</h3>
      <p>${escapeHtml(result.assistantText || "")}</p>
      ${result.clarificationQuestion ? `<p>${escapeHtml(result.clarificationQuestion)}</p>` : ""}
    </div>
    <div class="template-section">
      <h3>候选绑定</h3>
      ${renderCandidateBinding(result.candidateBinding)}
    </div>
    <div class="template-section">
      <h3>动作草稿</h3>
      <pre>${escapeHtml(JSON.stringify(result.actionDraft || {}, null, 2))}</pre>
    </div>
    <div class="template-section">
      <h3>ActionPlan</h3>
      ${renderActionPlan(result.actionPlan)}
      <pre>${escapeHtml(JSON.stringify(result.actionPlan || {}, null, 2))}</pre>
    </div>
    <div class="template-section">
      <h3>执行桥接草稿</h3>
      ${renderActionBridgePreview(actionBridgePreview)}
      <pre>${escapeHtml(JSON.stringify(result.backendActionDraft || {}, null, 2))}</pre>
    </div>
    <div class="template-section">
      <h3>完整 JSON</h3>
      <pre>${escapeHtml(JSON.stringify(result, null, 2))}</pre>
    </div>
  `;
}

function buildActionBridgePreview(draft) {
  if (!window.AliciaActionBridgeAdapter || !draft || !draft.actionType) {
    return null;
  }
  return window.AliciaActionBridgeAdapter.buildPreview(draft, state.actionBridgeContract);
}

function renderActionBridgePreview(preview) {
  if (!preview) {
    return "<p>暂无可预检的执行草稿。</p>";
  }
  const request = preview.request;
  const errors = preview.errors.map((error) => `<li>${escapeHtml(error)}</li>`).join("");
  const warnings = preview.warnings.map((warning) => `<li>${escapeHtml(warning)}</li>`).join("");
  return `
    <div class="bridge-preview ${preview.valid ? "ok" : "blocked"}">
      <p>${preview.submitAllowed ? "可提交给 CloudStorageApi" : "不可直接提交"}</p>
      ${request ? `<p>${escapeHtml(request.method)} ${escapeHtml(request.url)}</p>` : ""}
      ${request?.contentType ? `<p>Content-Type：${escapeHtml(request.contentType)}</p>` : ""}
      ${errors ? `<ul class="validation-list error">${errors}</ul>` : ""}
      ${warnings ? `<ul class="validation-list warning">${warnings}</ul>` : ""}
    </div>
  `;
}

function renderActionPlan(plan) {
  if (!plan) {
    return "<p>No ActionPlan.</p>";
  }
  const fields = [
    ["status", plan.status],
    ["kind", plan.planKind],
    ["action", plan.actionType],
    ["risk", plan.risk],
    ["confirm", plan.confirmationLevel]
  ].map(([name, value]) => `${escapeHtml(name)}: ${escapeHtml(value || "")}`).join(" / ");
  const steps = (plan.steps || []).map((step, index) => `
    <p>${index + 1}. ${escapeHtml(step.action || "")} / ${escapeHtml(step.status || "")}</p>
  `).join("");
  const bindings = renderActionPlanBindings(plan.bindings || {});
  return `
    <div class="bridge-preview ${plan.status === "ready_to_execute" ? "ok" : "blocked"}">
      <p>${fields}</p>
      ${plan.summary ? `<p>${escapeHtml(plan.summary)}</p>` : ""}
      ${bindings}
      ${steps || "<p>No executable steps yet.</p>"}
    </div>
  `;
}

function renderActionPlanBindings(bindings) {
  const rows = Object.entries(bindings).map(([key, binding]) => {
    const candidates = binding.candidates || [];
    const previewRows = candidates.slice(0, 5).map((candidate, index) => `
      <p>
        ${index + 1}. ${escapeHtml(candidate.name || "")}
        ${candidate.type ? ` / ${escapeHtml(candidate.type)}` : ""}
        ${candidate.nodeId ? ` / #${escapeHtml(candidate.nodeId)}` : ""}
        ${candidate.path ? `<br /><span>${escapeHtml(candidate.path)}</span>` : ""}
      </p>
    `).join("");
    return `
      <div>
        <p>
          ${escapeHtml(key)}：
          ${escapeHtml(binding.kind || "")} /
          ${escapeHtml(binding.status || "")} /
          ${escapeHtml(binding.count ?? candidates.length)}
        </p>
        ${previewRows}
      </div>
    `;
  }).join("");
  return rows || "";
}

function renderCandidateBinding(binding) {
  if (!binding) {
    return "<p>未执行候选绑定。</p>";
  }
  const candidates = binding.candidates || [];
  const selected = binding.selectedCandidate;
  const selectedText = selected ? `
    <p>
      已选：第 ${escapeHtml(binding.selectedIndex || "")} 项，
      ${escapeHtml(selected.name || "")}
      ${selected.type ? ` / ${escapeHtml(selected.type)}` : ""}
      ${selected.nodeId ? ` / #${escapeHtml(selected.nodeId)}` : ""}
    </p>
  ` : "";
  const rows = candidates.map((candidate, index) => `
    <p>
      ${index + 1}. ${escapeHtml(candidate.name || "")}
      ${candidate.type ? ` / ${escapeHtml(candidate.type)}` : ""}
      ${candidate.nodeId ? ` / #${escapeHtml(candidate.nodeId)}` : ""}
      ${candidate.path ? `<br /><span>${escapeHtml(candidate.path)}</span>` : ""}
    </p>
  `).join("");
  return `
    <p>${escapeHtml(binding.status || "")}：${escapeHtml(binding.message || "")}</p>
    ${binding.query ? `<p>线索：${escapeHtml(binding.query)}</p>` : ""}
    ${selectedText}
    ${rows || "<p>暂无候选。</p>"}
  `;
}

function intentLabel(intent) {
  return state.clientConfig.intentLabels?.[intent] || intent;
}

function label(field) {
  return state.clientConfig.fieldLabels?.[field] || field;
}

function formatConfidence(value) {
  return typeof value === "number" ? value.toFixed(2) : "";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
