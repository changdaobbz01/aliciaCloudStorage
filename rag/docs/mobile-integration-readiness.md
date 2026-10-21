# Alicia RAG Mobile Integration Readiness

本文档记录移动端接入 RAG 与云端执行编排时必须保持的当前契约。机器可读版本以以下配置为准：

- `rag/conversation/mobile_contract.json`
- `rag/conversation/acceptance_scenarios.json`
- `rag/conversation/action_plan_schema.json`

## 当前状态

截至 2026-10-21，阶段 7 已删除 Android 本地 RAG 写执行器。生产运行在 `admin-single`，仅 `RAG_ADMIN + FOLDER_CREATE` 可确认执行；Android 0.1.13 已按同一角色门禁接入云端任务。批量、组合和其他单动作虽然已完成代码与真机验收，但尚未加入生产 allowlist。

## 不可突破的边界

- RAG 只负责理解、候选绑定和生成类型化 `ActionPlan`。
- 所有云盘写操作必须先登记为不可变 `ragExecution` 任务。
- 移动端只提交 `executionId + expectedVersion`，不得提交或解释 method/path/body。
- 公网 `backendActionDraft` 只保留只读兼容元数据，路由和请求体字段必须被移除，`executableByBackend=false`。
- 缺少 `executionReference`、云端开关关闭、确认失败或状态不一致时必须阻断，不能回退到本地写入。
- CloudStorageApi 是最终业务安全边界，按当前任务所有者重新校验资源归属、状态和版本。
- 本地文件字节只能在任务进入 `WAITING_CLIENT_INPUT` 后上传；页面导航、预览、下载和系统分享继续属于客户端能力。

## 移动端模块职责

| 模块 | 职责 |
| --- | --- |
| `RagAssistantClient` | 调用计划接口并解析稳定响应字段。 |
| `RagConversationStore` | 保存 `conversationId`、候选和当前任务上下文。 |
| `RagExecutionClient` | 确认、取消、查询不可变云端任务。 |
| `RagClientInputCoordinator` | 仅处理 `WAITING_CLIENT_INPUT` 所需的文件选择与字节上传。 |
| `RagReviewPresenter` | 展示候选、范围、风险、确认控件和任务结果。 |

仓库中不得重新引入 `RagActionExecutor`、本地 Action Bridge allowlist 执行器或 `RAG_ACTION_EXECUTION_ENABLED` 一类兼容开关。

## 能力状态

| 能力 | 客户端职责 | 生产状态 |
| --- | --- | --- |
| 对话、澄清、搜索和导航 | 展示响应与候选 | 已启用 |
| 新建文件夹 | 确认并观察云端任务 | 仅 `RAG_ADMIN` 灰度 |
| 重命名、移动、回收站、分享 | 展示类型化计划；缺少生产 allowlist 时阻断 | 未开放 |
| 批量移动、批量回收站、批量重命名 | 展示精确范围和风险；通过云端任务执行 | 未开放 |
| 新建文件夹后上传 | 云端先创建目录；等待输入后上传字节 | 代码和真机验收完成，生产未开放 |
| 永久删除、账号和管理员配置 | 不生成可执行任务 | 禁止 |

## 每次发版门禁

- Android Debug 与 Release 单元测试全部通过。
- 构建产物中不存在旧本地写执行开关。
- 有 `executionReference` 时只能进入 `RagExecutionClient`。
- 没有 `executionReference` 的写计划显示不可执行提示。
- `SUCCEEDED` 前不发送文件刷新信号。
- 云端任务确认、重复确认、取消、超时、重启恢复和回执一致性通过。
- 公网同步与 SSE 响应都不包含 `backendActionDraft` 的 method/path/body 等路由材料。
- 生产开关、角色和动作 allowlist 与发布记录完全一致。

## 验收顺序

1. 运行 Android 单测、Release 构建和连接设备测试。
2. 运行 RAG、`ragExecution` 和 CloudStorageApi 全量回归。
3. 验证 Compose、内部路由屏蔽和公开健康接口。
4. 使用专用 `RAG_ADMIN` 账号完成无写入取消探针。
5. 需要真实写入时使用唯一 `rag-active-` 名称，验证幂等后通过正式 Cloud API 清理。
6. 未经单独批准不得扩大角色、动作或组合 allowlist。
