# Alicia RAG Mobile Integration Readiness

本文档记录移动端接入 RAG 与云端执行编排时必须保持的契约、验收和执行边界。机器可读版本以以下配置为准：

- `rag/conversation/mobile_contract.json`
- `rag/conversation/acceptance_scenarios.json`
- `rag/conversation/action_bridge.json`

## 接入原则

- 移动端消费字符串枚举，例如 `intentId`、`nextAction`、`actionPlan.actionType`，不要转换成 `type = 1` 这类数字码。
- RAG 只负责理解、引导、候选绑定和计划生成，不直接执行真实文件变更。
- 移动端不执行云盘写操作草案，只使用类型化 API 确认、取消和查询 `ragExecution` 任务；旧本地执行开关保持关闭。
- CloudStorageApi 仍是最终安全边界，必须用当前登录用户鉴权并重新校验节点归属、状态、目录类型和重名冲突。
- 所有写操作必须经过可见确认；删除和批量动作必须展示路径、数量和影响范围。

## 移动端建议分包

建议后续在 `phoneAppAdd` 中按职责拆出以下模块，保持高内聚、低耦合：

| 模块 | 职责 |
| --- | --- |
| `RagAssistantClient` | 调用 `/api/assistant/plan`，解析稳定响应字段。 |
| `RagConversationStore` | 保存 `conversationId`、候选和当前 `executionReference`。 |
| `RagExecutionClient` | 使用 `executionId + expectedVersion` 确认、取消、轮询和订阅云端任务。 |
| `RagClientInputCoordinator` | 仅处理 `WAITING_CLIENT_INPUT` 所需的本地文件选择与上传输入。 |
| `RagReviewPresenter` | 生成候选选择、客户端上传、批量预览、最终确认控件所需展示数据。 |

UI 只依赖这些模块，不直接解析 action bridge 的低层请求字段。

## 第一阶段建议接入范围

当前生产处于 `shadow`：计划可登记，但公网确认、Worker、Cloud 分派和动作 allowlist 均关闭。移动端可以展示识别与评审结果，不得把 shadow 任务当成已经执行。

建议开放：

- `respond_only`：安安身份、闲聊、产品问答、越界请求、兜底。
- `file_search`：展示 RAG 返回的搜索候选。
- `file_delete`：单文件移入回收站，需候选确认和最终确认；生产 shadow 不执行。
- `file_share`：创建分享链接，需候选确认和最终确认；生产 shadow 不执行。
- `file_upload`：RAG 只定位目标目录，移动端展示“选择文件”客户端动作，选择本地文件后调用现有上传流程。
- `folder_create_then_upload`：移动端展示“新建并选择文件”，选择本地文件后先创建文件夹再上传，不让 RAG 接触本地文件。
- `collection_delete_by_name`、`collection_delete_by_category`：展示命中数量、筛选条件、预览列表和不完整预览阻断提示；生产真实提交关闭。
- `collection_move_by_extension`、`collection_move_by_name`：展示命中数量、筛选条件、目标目录和预览列表；生产真实提交关闭。

需要补齐后再开放：

- `file_rename`：`phoneAppAdd` 已补齐 Retrofit 方法、`RenameNodePayload`、repository wrapper、候选点击回传、基础最终确认控件和默认关闭的 executor 路由；开放真实执行前仍需显式启用提交。

暂不开放真实执行：

- `collection_rename_add_prefix`：当前只做受控识别和 ActionPlan 扩展位，action bridge 未暴露执行。
- 永久删除、管理员能力、账号配置、复杂分享保存编排。

## 执行前门禁

每次准备把 RAG 能力接入 App 前，至少完成：

- RAG 单测通过。
- `acceptance_scenarios.json` 中自动场景全部通过。
- 调试 Web 使用真实 `Authorization` 跑通候选绑定。
- 多候选、无候选、重名目录、集合预览不完整这些人工场景有明确 UI 状态。
- 有 `executionReference` 时必须使用云端任务通道，不得回退执行 `backendActionDraft`。
- `ALICIA_RAG_ACTION_EXECUTION_ENABLED=false` 必须保持关闭；云端通道失败时也不得静默转为本地写入。
- 只有任务进入 `SUCCEEDED` 后，移动端才通过 `AiChatFileMutationSignal` 刷新文件页和回收站；客户端上传沿用现有成功刷新流程。
- DeepSeek API key 通过环境变量注入，不提交到仓库。
- 危险动作确认文案包含名称、路径、数量和操作后果。

## 当前移动端与生产开关状态

| 能力 | 后端 | RAG | 移动端现状 | 处理建议 |
| --- | --- | --- | --- | --- |
| 重命名 | 已有 | 已桥接 | 候选选择与评审 UI 已有；旧本地 executor 关闭 | 待对应云端动作进入明确 allowlist 后再做管理员灰度。 |
| 上传到已绑定目录 | 已有 | 已桥接 | `upload_target` 已展示客户端“选择文件”动作，并复用系统文件选择器和现有上传流程 | 保持 RAG 不接触本地文件 Uri，后续补选择取消后的 UI 恢复策略。 |
| 新建文件夹后上传 | 已有原子接口 | 已有 composite ActionPlan | 已接入客户端编排，选择文件后先 `createFolder` 再上传到新目录 | 继续补选择取消后的 UI 恢复策略和重名冲突提示。 |
| 批量删除 | 已有 | 已桥接 | 集合预览 UI 已有；生产执行关闭 | 完整预览、截断阻断和重新校验通过后才进入独立灰度。 |
| 批量移动 | 已有 | 已桥接 | 目标目录与集合预览 UI 已有；生产执行关闭 | 补齐多候选人工验收后再进入独立灰度。 |
| 批量前缀重命名 | 缺批量接口 | 仅规划 | 不开放 | 保持 planning only，后续单独设计批量 rename API。 |

## 调试顺序

1. 运行 `test/rag-e2e/run-evaluation.ps1 -Suite all`，同时通过基线和高压场景。
2. 在生产 `shadow` 观察登记成功率、误登记、过期和取消，不开启公网确认或 Worker。
3. 移动端验证 `respond_only`、`ask_clarification`、候选和 `executionReference` 展示。
4. 只对单一管理员动作启用 public confirm、Worker、Cloud dispatch 和精确 allowlist。
5. 验证幂等、超时、重启恢复和回退后，再逐项扩大动作范围。
