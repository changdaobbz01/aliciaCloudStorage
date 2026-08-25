# Action Bridge Client Adapter

本文档描述调用方如何安全消费 `backendActionDraft`。调用方可以是移动端、Web 调试台，或未来独立的 API 网关适配层。

## 适配器职责

适配器只做三件事：

- 校验 `backendActionDraft` 是否命中本地 allowlist。
- 把 `method/path/queryParameters/body/contentType` 转成调用方自己的请求对象。
- 根据 `status/nextAction/requiredClientFields` 决定是否允许提交。

适配器不负责：

- 不解析用户自然语言。
- 不修改 RAG 返回的 `nodeId`。
- 不绕过 CloudStorageApi 权限校验。
- 不自动执行删除、分享、重命名等变更。

## 最小 allowlist

调用方必须至少校验：

- `actionType` 存在于客户端内置的版本化 allowlist；`GET /api/assistant/contracts/action-bridge` 是 `RAG_ADMIN` 诊断接口，不作为普通用户运行时依赖。
- `method` 等于契约配置中的 `method`。
- `pathTemplate` 等于契约配置中的 `path_template`。
- `nextAction` 等于契约配置中的 `next_action`。
- `status` 等于契约配置中的 `status`。
- `path` 必须是相对路径，必须以 `/api/` 开头，不能包含协议、双斜杠、`..` 或反斜杠。
- 单对象请求必须有 `targetCandidate.nodeId`。
- 批量请求必须有非空 `body.nodeIds`，且每个值都是合法正整数。

## 提交策略

`backend_action_ready`：

- 可生成 CloudStorageApi 请求预览。
- 可在用户确认 UI 完成后提交。
- 必须携带当前客户端保存的用户 `Authorization`。
- 批量删除/移动只消费 `body.nodeIds` 和可选 `body.parentId`，不要从展示列表重新拼接请求。

`client_action_required`：

- 只能进入客户端后续流程。
- 例如 `upload_target` 只表示目标目录已确认。
- 客户端必须再让用户选择本地文件，再按上传接口提交。

其他状态：

- 一律不允许提交。
- 应展示 RAG 返回的 `message` 或回到候选选择/确认流程。

## 调试实现

本地调试页提供了一个纯前端参考实现：

- `test/public/actionBridgeAdapter.js`
- `test/actionBridgeAdapter.test.js`

该实现不会发起真实请求，只输出：

- `valid`
- `submitAllowed`
- `errors`
- `warnings`
- `request.method`
- `request.url`
- `request.contentType`
- `request.body`

移动端可以按相同规则实现 Kotlin 版本的适配器，或者后续由 API 网关做统一适配。
