# Alicia RAG Mobile Integration Readiness

本文档记录移动端正式接入 RAG 前必须收口的契约、验收和执行边界。机器可读版本以以下配置为准：

- `rag/conversation/mobile_contract.json`
- `rag/conversation/acceptance_scenarios.json`
- `rag/conversation/action_bridge.json`

## 接入原则

- 移动端消费字符串枚举，例如 `intentId`、`nextAction`、`actionPlan.actionType`，不要转换成 `type = 1` 这类数字码。
- RAG 只负责理解、引导、候选绑定和计划生成，不直接执行真实文件变更。
- 移动端只通过本地 allowlist 执行动作，不能执行 RAG 返回的任意 URL。
- CloudStorageApi 仍是最终安全边界，必须用当前登录用户鉴权并重新校验节点归属、状态、目录类型和重名冲突。
- 所有写操作必须经过可见确认；删除和批量动作必须展示路径、数量和影响范围。

## 移动端建议分包

建议后续在 `phoneAppAdd` 中按职责拆出以下模块，保持高内聚、低耦合：

| 模块 | 职责 |
| --- | --- |
| `RagAssistantClient` | 调用 `/api/assistant/plan`，解析稳定响应字段。 |
| `RagConversationStore` | 保存 `conversationId`、待选候选、待确认计划。 |
| `RagActionBridgeValidator` | 校验 `backendActionDraft` 是否命中本地 allowlist。 |
| `RagActionExecutor` | 把已验证动作分发到现有 `AliciaRepository` 方法。 |
| `RagReviewPresenter` | 生成候选选择、批量预览、最终确认 UI 所需展示数据。 |

UI 只依赖这些模块，不直接解析 action bridge 的低层请求字段。

## 第一阶段建议接入范围

第一阶段目标是“能聊、能识别、能展示、能安全确认”，不追求一次性执行所有复杂动作。

建议开放：

- `respond_only`：安安身份、闲聊、产品问答、越界请求、兜底。
- `file_search`：展示 RAG 返回的搜索候选。
- `file_delete`：单文件移入回收站，需候选确认和最终确认。
- `file_share`：创建分享链接，需候选确认和最终确认。
- `file_upload`：RAG 只定位目标目录，移动端选择本地文件后上传。
- `folder_create_then_upload`：新建文件夹后上传，先走客户端编排，不让 RAG 执行。

需要补齐后再开放：

- `file_rename`：后端已支持 `PUT /api/storage/nodes/{nodeId}/rename`，但 `phoneAppAdd` 还缺 Retrofit 方法、`RenameNodePayload` 和 repository wrapper。
- `collection_delete_by_name`、`collection_delete_by_category`：需要移动端批量预览确认 UI。
- `collection_move_by_extension`、`collection_move_by_name`：需要目标目录候选选择和集合预览确认 UI。

暂不开放真实执行：

- `collection_rename_add_prefix`：当前只做受控识别和 ActionPlan 扩展位，action bridge 未暴露执行。
- 永久删除、管理员能力、账号配置、复杂分享保存编排。

## 执行前门禁

每次准备把 RAG 能力接入 App 前，至少完成：

- RAG 单测通过。
- `acceptance_scenarios.json` 中自动场景全部通过。
- 调试 Web 使用真实 `Authorization` 跑通候选绑定。
- 多候选、无候选、重名目录、集合预览不完整这些人工场景有明确 UI 状态。
- `backendActionDraft` 命中本地 allowlist 后才可提交。
- DeepSeek API key 通过环境变量注入，不提交到仓库。
- 危险动作确认文案包含名称、路径、数量和操作后果。

## 当前已发现的移动端适配缺口

| 能力 | 后端 | RAG | 移动端现状 | 处理建议 |
| --- | --- | --- | --- | --- |
| 重命名 | 已有 | 已桥接 | 缺 wrapper | 接入前补 `RenameNodePayload`、Retrofit `renameNode`、Repository `renameNode`。 |
| 新建文件夹后上传 | 已有原子接口 | 已有 composite ActionPlan | 缺 RAG 编排器 | 在 `RagActionExecutor` 中先 `createFolder`，成功后再打开文件选择并上传。 |
| 批量删除 | 已有 | 已桥接 | 有 repository 方法 | 先补集合预览确认 UI，再开放。 |
| 批量移动 | 已有 | 已桥接 | 有 repository 方法 | 先补目标目录选择和集合预览确认 UI，再开放。 |
| 批量前缀重命名 | 缺批量接口 | 仅规划 | 不开放 | 保持 planning only，后续单独设计批量 rename API。 |

## 调试顺序

1. 用 `http://127.0.0.1:8092/` 跑自动验收话术，确认识别稳定。
2. 粘贴真实登录 `Authorization`，验证候选绑定和 `ActionPlan`。
3. 验证用户确认后 `backendActionDraft` 的 method/path/body 是否和 CloudStorageApi 一致。
4. 移动端先接 `respond_only`、`ask_clarification`、候选展示。
5. 再接单动作执行。
6. 最后接组合动作和集合动作。
