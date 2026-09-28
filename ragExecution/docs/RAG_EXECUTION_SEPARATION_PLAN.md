# Alicia RAG 执行服务分离实施方案

## 1. 文档信息

- 状态：实施基线有效；阶段 0 至阶段 4 已完成代码与隔离容器验收，阶段 5 已完成 Android 单动作真机端到端验收，阶段 6 已完成代码、自动化回归和 Android 真机组合链路验收；全部开关默认关闭，尚未进行生产切换
- 日期：2026-09-28
- 目标目录：`AliciaCloudStorage/ragExecution`
- 适用范围：`rag`、`ragExecution`、`CloudStorageApi`、`phoneAppAdd`、`webApp`、Compose、Nginx 与发布脚本
- 后续识别优化：必须在执行分离完成并稳定验收后开始

本文档是本次改造的单一事实来源。实现过程中如需改变边界、状态机、安全模型或数据所有权，必须先更新本文档并说明原因，不能直接在代码中形成第二套事实。

### 1.1 当前实施记录

2026-09-18 已完成：

- 将 `ragExecution` 加入 Maven reactor，并建立独立 Spring Boot 模块与 Dockerfile。
- 增加独立数据库初始化、Flyway V1 三表结构、JPA Repository 和乐观锁。
- 建立集中状态机、类型化首批动作、Clock/ID 端口和数据库任务租约基础。
- Compose 增加仅本机回环的 `rag-execution` 服务与兼容已有数据卷的一次性数据库初始化服务；二者均受 `rag-execution-foundation` profile 保护，不随现有默认发布启动。
- 增加空库重复迁移、应用上下文重启持久性、租约领取、状态机和隔离边界测试。
- 完成阶段 2 影子计划登记：HMAC-SHA256 服务签名、持久化 nonce 防重放、Identity 独立验权、类型化动作校验、计划 hash 和幂等登记。
- RAG 同步与 SSE 计划接口可在显式启用后登记计划并返回只读 `executionReference`；失败时保持原对话响应不变，仅输出脱敏告警。
- 完成阶段 3 CloudStorageApi 受控动作入口：独立 HMAC 服务身份、持久化 nonce、防任意 URL/HTTP 目标、五类严格动作、actor 资源校验、JPA 乐观版本与事务内动作回执。
- 同一 `stepId` 的顺序重试和 MySQL 并发竞争均只产生一次业务变更；相同 step 不同内容、越权和过期版本稳定拒绝；Nginx 显式对 `/internal/rag-execution/**` 返回 404。
- 完成阶段 4 公开控制面：所有者查询与取消、`RAG_ADMIN` 乐观锁确认、事件轮询、支持 `Last-Event-ID` 恢复的 SSE，以及 payload 脱敏。
- 完成数据库租约 Worker：MySQL `SKIP LOCKED` 领取、租约续期/回收、过期与重试调度、显式动作 allowlist、单步骤/单对象防线和固定 Cloud 目标分派。
- 模糊失败和 Worker 重启复用原 `stepId` 与 `requestHash`；Cloud 已执行但响应丢失时，通过原事务回执安全收敛，不重复业务变更。
- Nginx 增加 `/rag-execution/**` 外部控制面转发，并同时屏蔽内部动作路径和执行服务内部登记路径。
- 后端 Maven 回归共 `592` 项测试通过：CloudStorageApi `270`、RAG `281`、ragExecution `41`。
- Android 回归通过：正式客户端 `phoneAppAdd` `188` 项、历史兼容客户端 `phoneApp` `5` 项；Debug APK、Release 构建和统一 release readiness 均通过。
- 完成阶段 5 现有 Android 客户端切换：类型化调用确认、查询和取消接口，按固定任务 ID 轮询状态，确认响应丢失时查询恢复。
- 完成阶段 6 代码：批量删除/移动/重命名的不可变快照与执行前复核、顺序步骤依赖和白名单输出引用、`FOLDER_CREATE -> UPLOAD_FILES` 客户端输入工作流、超时终止及 Android 上传结果回报。
- 上传成功声明会经固定 HMAC 通道回到 CloudStorageApi 复核节点所有者、文件类型和目标父目录；客户端不能仅凭自报节点 ID 把任务标记成功。
- 云端 `executionReference` 永远优先于旧草稿；新通道关闭或失败时不自动回退本地写操作。旧执行器仅保留一个默认关闭的兼容周期。
- 当前 `webApp` 没有 RAG 确认交互或旧 RAG 写执行调用，因此没有 Web 执行路径需要迁移；未来新增时必须直接使用本契约。
- 所有开关仍默认 `false`；旧路径删除和生产流量均未启用。

真实 MySQL 8 隔离容器验收已通过：执行服务 V1-V4 迁移与持久性、计划签名登记和幂等，以及 CloudStorageApi V20 迁移、开关关闭 404、开启后无签名 401、合法签名动作、持久化防重放、顺序重试和双请求并发幂等。阶段 4 追加验证了 Identity/Cloud 依赖健康、管理员确认、MySQL 原生任务领取、首次 Cloud 响应丢失后的 `RETRY_WAIT`、相同 step 回执恢复、单次业务变更、终态事件和重复确认幂等。阶段 5 Android 单测与构建独立验证，客户端开关保持关闭；测试容器、网络和专用镜像在验收后清理。

## 2. 当前基线

当前调用链为：

```text
用户输入
  -> rag 识别、候选绑定、生成 ActionPlan/BackendActionDraft
  -> 客户端校验本地 allowlist
  -> 客户端携带用户 Token 调用 CloudStorageApi
```

现状中的关键事实：

- `rag` 负责语义识别、只读候选绑定和执行草稿生成。
- `phoneAppAdd` 已存在 `RagActionExecutor`，但生产执行开关默认关闭。
- 客户端执行器已经复制了重命名、删除、移动、分享、建目录和批量动作的调度逻辑。
- CloudStorageApi 已具备真实业务接口和最终用户资源校验。
- RAG 会话当前保存在单机内存中，不适合作为可靠执行任务存储。
- CloudStorageApi 已有 Flyway 和持久化重试任务的实现经验，但 RAG 执行数据不应混入云盘业务数据库所有权。

本次改造冻结 RAG 的现有意图、语料、Prompt、SemanticFrame 和 ActionPlan 语义。除修复阻塞分离的契约错误外，不在同一阶段调整识别结果。

## 3. 目标与非目标

### 3.1 目标

1. 将 RAG 写操作从客户端执行迁移为云端受控执行。
2. 将识别、编排和领域执行拆成清晰的三个所有权边界。
3. 所有写操作必须经过服务端计划登记、用户确认、再次校验和幂等执行。
4. 任务在客户端退出、网络中断或服务重启后仍可恢复。
5. 单动作、批量动作和组合动作共用同一个可审计状态机。
6. 执行失败可分类、可观察、可安全重试；未知结果不得盲目重复写入。
7. 保留上传等必须依赖客户端本地文件输入的特殊路径。
8. 为执行分离后的识别能力优化提供稳定、安全的承载层。

### 3.2 非目标

- 不在本阶段提升意图识别准确率。
- 不建设允许模型自由调用任意 HTTP API 的通用 Agent 平台。
- 不将 CloudStorageApi 的业务服务、实体或 Repository 复制到 `ragExecution`。
- 不让 `ragExecution` 直接连接 CloudStorageApi 的业务表。
- 不引入跨服务 XA 分布式事务。
- 第一阶段不引入 Kafka、RabbitMQ 或复杂工作流引擎。
- 不在第一批开放永久删除、管理员操作、账号操作和任意脚本执行。

## 4. 最终职责边界

| 组件 | 唯一职责 | 明确禁止 |
| --- | --- | --- |
| `rag` | 自然语言理解、SemanticFrame、只读候选绑定、ActionPlan、确认摘要 | 直接执行业务写操作、保存执行任务、生成任意 URL |
| `ragExecution` | 计划登记、确认、状态机、任务租约、动作编排、重试、审计、结果汇总 | 直连云盘业务表、复制文件业务规则、保存用户 Token |
| `CloudStorageApi` | 资源归属、业务校验、事务、真实文件/目录/分享操作、执行回执 | 信任 RAG 自报结果、绕过现有服务层 |
| 客户端 | 展示计划、确认/取消、订阅状态、提供本地文件、展示结果 | 拼装或执行云盘写请求、解释任意后端路径 |
| Identity | 用户和角色真相、会话状态 | 承载 RAG 执行任务 |

## 5. 目标架构

```text
                    +----------------------+
用户自然语言 ------> | rag                  |
                    | 识别/绑定/计划        |
                    +----------+-----------+
                               |
                               | 内部登记受控计划
                               v
                    +----------------------+
客户端确认/取消 ---> | ragExecution         |
客户端查询/SSE <---- | 状态机/队列/编排/审计 |
                    +----------+-----------+
                               |
                               | 服务签名 + typed action
                               v
                    +----------------------+
                    | CloudStorageApi      |
                    | 最终校验/事务/真实执行 |
                    +----------------------+
```

基础原则：

- `rag` 只能提交版本化、类型化的计划，不能提交任意网络请求。
- 客户端不能把 RAG 响应原样转交为执行载荷。
- `ragExecution` 只允许调用固定 CloudStorageApi 主机和固定动作协议。
- CloudStorageApi 内部执行入口必须复用现有 `StorageCommandService`、`ShareLinkService` 等业务服务。
- 普通云盘接口不依赖 `ragExecution` 可用；新服务故障不能影响用户手工操作云盘。

## 6. 动作契约

### 6.1 禁止的契约

以下结构不得进入新的执行协议：

```json
{
  "method": "DELETE",
  "path": "/api/storage/nodes/123",
  "body": {}
}
```

原因：它把网络路由权交给模型或客户端，难以做类型校验、版本治理、权限收口和兼容演进。

### 6.2 受控动作枚举

第一批服务端动作：

```text
NODE_RENAME
NODE_TRASH
NODE_MOVE
FOLDER_CREATE
SHARE_CREATE
NODE_BATCH_TRASH
NODE_BATCH_MOVE
NODE_BATCH_RENAME
```

客户端参与动作：

```text
UPLOAD_FILES
DOWNLOAD_TO_DEVICE
OPEN_PREVIEW
NAVIGATE_UI
SYSTEM_SHARE
```

后续组合动作只能由上述原子动作组成。模型不能动态发明动作名。

### 6.3 类型化步骤示例

```json
{
  "actionType": "NODE_RENAME",
  "payload": {
    "nodeId": 123,
    "expectedNodeVersion": 7,
    "newName": "最终报告.pdf"
  }
}
```

核心领域代码不得使用 `Map<String, Object>` 表达执行参数。每个动作使用独立 Java record，并在入口完成严格反序列化和 Bean Validation。

### 6.4 当前动作到新动作映射

| 当前 RAG actionType | 新动作 | 第一批 |
| --- | --- | --- |
| `rename` | `NODE_RENAME` | 是 |
| `delete` | `NODE_TRASH` | 是 |
| `share` | `SHARE_CREATE` | 是 |
| `folder.create` | `FOLDER_CREATE` | 是 |
| `collection.trash*` | `NODE_BATCH_TRASH` | 第二批 |
| `collection.move*` | `NODE_BATCH_MOVE` | 第二批 |
| `collection.rename*` | `NODE_BATCH_RENAME` | 第二批 |
| `upload_target` | `UPLOAD_FILES` | 第三批，客户端参与 |
| `composite.*` | 原子步骤集合 | 第三批 |

## 7. 外部与内部 API

### 7.1 客户端公开 API

统一挂载到 `/rag-execution/api/**`，由 Nginx 转发到独立 upstream：

```http
GET  /rag-execution/api/executions/{executionId}
POST /rag-execution/api/executions/{executionId}/confirm
POST /rag-execution/api/executions/{executionId}/cancel
GET  /rag-execution/api/executions/{executionId}/events
GET  /rag-execution/api/executions/{executionId}/stream
GET  /rag-execution/api/health
GET  /rag-execution/api/health/dependencies
```

规则：

- 所有业务接口必须携带当前用户 Authorization。
- 查询、确认和取消只能访问属于当前用户的任务。
- 确认请求只提交 `expectedVersion` 和可选客户端能力信息，不重新提交 ActionPlan。
- 重复确认同一版本返回同一任务状态，不重复入队。
- 已过期、已取消或终态任务不可重新确认。
- SSE 只发送脱敏状态事件；断线后可用事件序号续传。

确认请求示例：

```json
{
  "expectedVersion": 3
}
```

### 7.2 RAG 内部计划登记 API

仅允许 `rag` 容器通过内部网络和服务签名调用：

```http
POST /internal/executions
```

登记内容包括：

- `planSchemaVersion`
- `sourceResponseId`
- `conversationId`
- `intentId`
- `planId`
- `planHash`
- `risk`
- `summary`
- 类型化 `steps`
- 候选与影响范围快照
- `expiresAt`

登记接口必须验证：

- 计划版本受支持。
- 动作和步骤数量命中 allowlist。
- 计划摘要与执行参数中不存在任意 URL、HTTP 方法或内部存储字段。
- 用户 Token 只用于本次 Identity 校验，绝不写入数据库或日志。
- 同一用户、同一 `planId + planHash` 重复登记返回同一 `executionId`。

### 7.3 CloudStorageApi 内部动作 API

新增仅容器内可达的受控入口：

```http
POST /internal/rag-execution/actions
```

请求信封包含：

```text
executionId
stepId
actorUserId
actionType
payload
requestHash
issuedAt
```

CloudStorageApi 必须：

1. 验证 `ragExecution` 服务签名、时间窗口和请求摘要。
2. 按 `executionId + stepId` 做幂等回执查询。
3. 验证 actor、节点归属、节点状态、目标目录、名称冲突和业务限制。
4. 把类型化载荷转换为现有业务 DTO。
5. 调用现有业务 Service，不直接操作 Repository 绕过规则。
6. 在业务事务中保存执行回执；重复请求返回原回执。

该入口不得通过 Nginx 暴露到公网。`/rag-execution/internal/**` 与 CloudStorageApi `/internal/rag-execution/**` 均应显式返回 404 或只存在于内部网络。

## 8. 身份与服务间安全

### 8.1 用户身份

- 创建计划时，`ragExecution` 独立调用 Identity 验证当前用户和 RAG 应用角色。
- 确认、取消和查询时再次验证当前用户。
- 数据库只保存稳定 `userId`、角色/策略版本和确认时间，不保存 Bearer Token。
- 执行任务不能以 Access Token 是否仍在内存中作为恢复前提。

### 8.2 服务身份

第一版使用独立服务密钥和 HMAC 请求签名，密钥不得复用 RAG webhook secret、JWT secret 或分享 secret。签名至少覆盖：

```text
HTTP method
canonical path
timestamp
nonce
body SHA-256
calling service
```

服务端要求：

- 最大时间偏差默认 60 秒。
- 相同 nonce 在时间窗口内不可重复使用。
- 常量时间比较签名。
- 日志不输出签名、Token、文件名、路径或完整 payload。
- 生产密钥只保存在服务端 `.env`。

后续如基础设施支持，可升级为 mTLS 或标准服务身份，但不改变动作契约。

### 8.3 执行授权

- `ragExecution` 在确认时记录用户身份与确认快照。
- CloudStorageApi 执行时仍按 `actorUserId` 重新检查资源所有权和业务状态。
- 高风险或等待时间超过策略窗口的任务必须进入 `EXPIRED`，要求用户重新生成并确认计划。
- 第一版确认到开始执行的最大等待时间建议为 2 分钟；等待超过该值不执行旧计划。

## 9. 状态机

### 9.1 状态定义

```text
PENDING_CONFIRMATION
QUEUED
RUNNING
RETRY_WAIT
WAITING_CLIENT_INPUT
SUCCEEDED
PARTIALLY_SUCCEEDED
FAILED
CANCELLED
EXPIRED
```

### 9.2 合法迁移

```text
PENDING_CONFIRMATION -> QUEUED
PENDING_CONFIRMATION -> CANCELLED
PENDING_CONFIRMATION -> EXPIRED

QUEUED -> RUNNING
QUEUED -> CANCELLED
QUEUED -> EXPIRED

RUNNING -> SUCCEEDED
RUNNING -> PARTIALLY_SUCCEEDED
RUNNING -> RETRY_WAIT
RUNNING -> WAITING_CLIENT_INPUT
RUNNING -> FAILED

RETRY_WAIT -> QUEUED
RETRY_WAIT -> FAILED
RETRY_WAIT -> EXPIRED

WAITING_CLIENT_INPUT -> QUEUED
WAITING_CLIENT_INPUT -> SUCCEEDED
WAITING_CLIENT_INPUT -> PARTIALLY_SUCCEEDED
WAITING_CLIENT_INPUT -> FAILED
WAITING_CLIENT_INPUT -> CANCELLED
WAITING_CLIENT_INPUT -> EXPIRED
```

终态：

```text
SUCCEEDED
PARTIALLY_SUCCEEDED
FAILED
CANCELLED
EXPIRED
```

状态迁移必须集中在领域对象或专用状态机中，Controller、定时任务和 Handler 不得直接任意写状态字符串。

### 9.3 并发控制

- `rag_execution.version` 使用乐观锁。
- Worker 使用数据库租约认领任务。
- 租约字段包括 `lease_owner`、`lease_until`、`heartbeat_at`。
- 认领使用事务和 `SELECT ... FOR UPDATE SKIP LOCKED` 或等价原子更新。
- 服务重启后，过期租约任务根据步骤回执恢复到 `QUEUED`、`RETRY_WAIT` 或终态。
- 任一任务同一时刻只能由一个 Worker 推进。

## 10. 数据所有权与表设计

### 10.1 独立数据库

`ragExecution` 使用独立数据库：

```text
默认 database：alicia_rag_execution
环境变量：ALICIA_RAG_EXECUTION_MYSQL_DATABASE
独立用户名：ALICIA_RAG_EXECUTION_DB_USERNAME
独立密码：ALICIA_RAG_EXECUTION_DB_PASSWORD
```

它可以与 CloudStorageApi 共用 MySQL 实例，但不能共用业务 database 或 Flyway 历史表。Compose 初始化脚本负责创建 database 和最小权限用户。

### 10.2 核心表

#### `rag_execution`

保存任务头、所有者、状态和确认快照：

```text
id
owner_user_id
conversation_id
source_response_id
plan_id
plan_schema_version
plan_hash
action_summary
risk
status
version
idempotency_key
expires_at
confirmed_at
queued_at
started_at
finished_at
lease_owner
lease_until
heartbeat_at
result_code
result_summary_json
error_code
error_message_safe
created_at
updated_at
```

唯一约束：

```text
(owner_user_id, idempotency_key)
(owner_user_id, plan_id, plan_hash)
```

#### `rag_execution_step`

保存类型化步骤快照及单步结果：

```text
id
execution_id
step_index
action_type
payload_schema_version
payload_json
payload_hash
status
attempts
available_at
started_at
finished_at
result_json
error_code
error_message_safe
created_at
updated_at
```

唯一约束：

```text
(execution_id, step_index)
```

#### `rag_execution_event`

追加式保存可审计状态事件：

```text
id
execution_id
sequence_no
event_type
visibility
public_payload_json
created_at
```

事件表只保存脱敏信息，不保存 Token、内部对象键、用户原始文件路径和模型完整 Prompt。

### 10.3 CloudStorageApi 回执表

CloudStorageApi 增加 `rag_execution_action_receipt`，用于解决网络超时后的未知结果：

```text
execution_id
step_id
request_hash
action_type
actor_user_id
status
response_json
error_code
created_at
completed_at
```

`execution_id + step_id` 唯一。相同键但不同 `request_hash` 必须拒绝，防止幂等键被复用执行不同内容。

## 11. 任务领取、重试和未知结果

### 11.1 初期队列方案

第一版使用 MySQL 持久化任务表加 Worker，不引入外部消息队列。理由：

- 当前流量和服务数量不需要额外中间件。
- 数据库任务与状态机可以在同一事务中提交。
- 更容易完成重启恢复、审计和本地验证。

只有当任务吞吐、跨系统事件或多消费者需求达到明确瓶颈时，才考虑消息队列。

### 11.2 重试分类

| 结果 | 处理 |
| --- | --- |
| 2xx 且回执成功 | 步骤成功 |
| 400/422 参数错误 | 永久失败，不重试 |
| 401/403 | 永久失败并记录授权错误 |
| 404 资源不存在 | 任务失效或永久失败 |
| 409 资源版本/名称冲突 | `FAILED`，要求重新计划 |
| 429 | 指数退避重试 |
| 5xx | 有上限的指数退避重试 |
| 连接超时/响应丢失 | 先按 stepId 查询回执，再决定是否重试 |

任何写操作在 CloudStorageApi 没有幂等回执前都不得自动重试。

默认策略建议：

```text
最大步骤数：10
最大批量节点数：100
最大计划载荷：64 KiB
确认有效期：10 分钟
确认后最大排队时间：2 分钟
最大自动尝试次数：3
退避：2s、10s、30s
```

所有限制都必须配置化，同时设置服务端硬上限，不能完全相信环境变量。

## 12. 组合动作与补偿

第一版组合动作采用 Saga 思路，不使用 XA：

- 步骤按明确顺序执行。
- 每一步都持久化开始、回执和结束状态。
- 后一步只能引用前一步显式声明的安全输出。
- 补偿必须逐动作声明，不能假设所有操作都可回滚。

示例：

```text
FOLDER_CREATE -> UPLOAD_FILES
```

- `FOLDER_CREATE` 可以由云端执行。
- `UPLOAD_FILES` 需要客户端本地字节，任务进入 `WAITING_CLIENT_INPUT`。
- 客户端上传成功后提交受控完成事件，任务才进入下一状态。
- 如果上传取消，不自动删除用户已经确认创建的文件夹，除非计划中明确展示并确认了补偿策略。

第一批不开放 `rename_then_share`、`move_then_share` 等组合动作。必须先完成单动作幂等和恢复验收。

## 13. 工程结构与代码纪律

建议包结构：

```text
ragExecution/
  pom.xml
  Dockerfile
  README.md
  docs/
  src/main/java/com/alicia/cloudstorage/ragexecution/
    api/                 # HTTP DTO 与薄 Controller
    application/         # 用例服务、事务边界
    domain/              # 状态机、动作类型、领域规则
    port/                # Identity、CloudStorage、时钟等端口
    infrastructure/
      identity/          # Identity 适配器
      cloud/             # CloudStorageApi 适配器
      persistence/       # JPA 与 Flyway 对应实现
      security/          # 服务签名、用户访问控制
    worker/              # 领取、租约、心跳和恢复
    config/
  src/main/resources/
    db/migration/
    application.properties
  src/test/
```

必须遵守：

1. Controller 只做协议校验和用例调用。
2. 状态迁移只能通过领域方法完成。
3. 一个动作一个 Handler，使用依赖注入注册表，不建立巨型 switch Controller。
4. 核心执行参数使用 record/enum/sealed interface，不使用无边界 Map。
5. 端口依赖由 application/domain 指向接口，基础设施实现接口。
6. 不创建含义不清的 `CommonService`、`Utils`、`Manager`、`Helper` 大杂烩。
7. 不跨模块共享 JPA Entity、Repository 或业务 Service 类。
8. 服务间使用版本化 JSON 契约和契约测试，不通过 Java 编译依赖偷共享内部实现。
9. 所有时间读取通过 Clock 端口，保证过期和重试测试可重复。
10. 所有 ID、状态和错误码有稳定类型，不散落魔法字符串。
11. 任何临时兼容代码必须带删除阶段和测试，不能成为永久双路径。
12. 新增文档不得重复本文内容；只链接到本文或记录专门的运行信息。

## 14. 可观测性

### 14.1 健康检查

```text
/api/health                仅表示进程存活
/api/health/dependencies   检查数据库、Identity、CloudStorageApi
```

模型服务不属于 `ragExecution` 依赖。RAG 不可用不影响已经确认和排队的任务继续执行。

### 14.2 指标

至少提供：

```text
executions_created_total{action_type}
executions_confirmed_total{action_type}
executions_terminal_total{status,action_type}
execution_queue_depth{status}
execution_wait_seconds
execution_duration_seconds{action_type}
execution_step_attempts_total{action_type,result}
execution_recovery_total{reason}
execution_expired_total{phase}
cloud_action_latency_seconds{action_type}
cloud_action_errors_total{action_type,error_class}
```

### 14.3 日志

允许记录：

- `executionId`
- `stepId`
- `actionType`
- 状态迁移
- 脱敏错误码
- 延迟和尝试次数

禁止记录：

- Authorization
- Cookie
- 服务签名和密钥
- 原始 Prompt
- 文件内容
- COS object key
- 用户本地路径
- 未脱敏的文件名和目录名

## 15. 分阶段实施

### 阶段 0：冻结基线与契约

变更：

- 保持 RAG 识别行为不变。
- 固定当前 ActionPlan、BackendActionDraft 和客户端执行器行为测试。
- 记录 RAG、CloudStorageApi 和 Android 当前测试基线。
- 确定首批动作和明确不支持项。

验收门槛：

- 现有 RAG 277 个测试通过。
- CloudStorageApi 251 个测试通过。
- `RagActionExecutor` 继续默认关闭。
- 形成动作映射和契约字段清单。

### 阶段 1：建立空执行服务与独立持久化

变更：

- 将 `ragExecution` 加入 Maven reactor。
- 增加 Spring Boot、Validation、JPA、Flyway、MySQL 和测试依赖。
- 创建独立数据库、V1 迁移和 Repository。
- 建立状态机、Clock、ID 生成和健康检查。
- Compose 增加 `rag-execution` 服务，默认仅本机回环端口 `127.0.0.1:8094`。
- 服务执行开关默认关闭。

验收门槛：

- Flyway 可在空库和重复启动场景稳定运行。
- 状态机非法迁移全部拒绝。
- 服务重启后任务仍存在。
- 此阶段不能调用任何 CloudStorageApi 写接口。

### 阶段 2：影子计划登记

变更：

- 实现 RAG 内部计划登记接口和服务签名。
- RAG 在计划进入最终确认阶段时影子登记任务。
- RAG 响应增加只读 `executionReference`：`executionId/status/version/expiresAt`。
- 客户端仍不确认、不执行新任务。

验收门槛：

- 同一计划重复登记只产生一个任务。
- 计划 hash、动作、用户和到期时间一致。
- 非白名单动作、任意 URL、超大载荷和签名重放全部拒绝。
- 影子登记失败不能改变当前 RAG 对话响应，但必须产生脱敏告警。

### 阶段 3：CloudStorageApi 受控执行入口

状态：**已完成代码与 MySQL 8 隔离容器验收；功能开关默认关闭，尚未由 Worker 调用。**

变更：

- CloudStorageApi 增加内部服务签名验证。
- 新增动作回执表和幂等分派入口。
- 实现 `NODE_RENAME`、`NODE_TRASH`、`NODE_MOVE`、`FOLDER_CREATE`、`SHARE_CREATE`。
- 每个动作适配现有 DTO 和业务 Service。

验收门槛：

- 同一 stepId 并发调用只产生一次业务变更。
- 同一 stepId 不同 payload 被拒绝。
- 资源不属于 actor 时返回 403，不能执行。
- 资源状态或版本变化时返回稳定冲突码。
- 内部接口不从公网可达。

### 阶段 4：确认 API、Worker 与管理员灰度

状态：**已完成代码、本地回归与真实 MySQL 8 隔离容器端到端验收；全部开关默认关闭，尚未启用生产灰度。**

变更：

- 开放查询、确认、取消、事件和 SSE。
- 实现数据库租约 Worker、心跳、过期和恢复。
- 仅允许 `RAG_ADMIN` 灰度确认执行。
- 首批只执行单对象动作。

验收门槛：

- 重复确认、并发确认和重复 Worker 认领均不重复执行。
- 执行中重启后可从回执恢复。
- 超时、5xx 和响应丢失经过回执查询后安全收敛。
- 用户 A 不能读取或确认用户 B 的任务。
- 停止执行开关后不领取新任务，但不破坏已有记录。

### 阶段 5：客户端切换

状态：现有 Android 确认客户端代码和真机端到端验收已完成；生产开关未启用。当前 Web 端无 RAG 确认界面，本阶段 Web 条目不适用，未来新增时直接接入 `ragExecution`，不得复制旧本地执行逻辑。

变更：

- 客户端确认按钮调用 `ragExecution`，不再调用 `RagActionExecutor.execute`。
- 客户端通过 SSE 或查询展示进度。
- 保留本地上传、页面导航、预览和系统分享能力。
- 旧 `RagActionExecutor` 保留一个兼容发布周期但始终默认关闭。

验收门槛：

- 客户端代码不再解释 method/path/body。
- 客户端退出后云端任务继续完成。
- Token 刷新不影响已经确认的云端任务。
- Web 与 Android 对同一任务展示一致状态。

当前实现说明：

- Android 使用状态查询展示进度；SSE 仍保留为公开协议能力，不是当前移动端依赖。
- 云端模式开启时确认路径只调用 `ragExecution`；缺少云端引用也会阻断。旧执行器只处理云端模式关闭、没有云端引用且显式打开兼容开关的旧响应。
- 任务确认后由云端任务池继续执行，ViewModel/页面退出只停止本地观察，不取消任务。
- 当前没有 Web RAG 任务展示面；“Web 与 Android 一致”在新增 Web 客户端后验收，不能为满足形式要求新增无入口死代码。

2026-09-28 真机验收：

- Android 13 真机通过 ADB reverse 连接本地 `rag:8081` 与 `ragExecution:8084`，CloudStorageApi 使用本地 `8090`，安装过程保留原有应用数据。
- 真机先验证真实生产 Identity 权限边界：当前账号不是 `RAG_ADMIN`，确认请求稳定返回 `rag_admin_confirmation_required`，没有入队或执行。
- 为避免修改生产账号和角色，正向链路仅在本机将执行服务切到一次性 Identity 测试桩；测试桩只返回同一任务所有者和 `RAG_ADMIN`，未写入仓库，验收后停止。
- 验收任务 `FOLDER_CREATE / RAG_E2E_20260928_1112` 在点击确认后立即强制停止客户端，最终状态为 `SUCCEEDED`，单步 `attempts=1`、结果码 `FOLDER_CREATED`。
- 事件严格按 `EXECUTION_REGISTERED -> EXECUTION_CONFIRMED -> EXECUTION_STARTED -> STEP_SUCCEEDED -> EXECUTION_SUCCEEDED` 产生；Cloud 动作回执与实际根目录节点同时存在，证明客户端退出不影响服务端任务完成。
- 验收取证完成后，以上两个测试 execution ID、对应事件/步骤/动作回执和唯一命名的测试文件夹已按精确条件从本地数据库清理；数据库卷和其他本地数据未删除。
- 真机发现确认卡片仍显示旧的“仅展示计划”占位说明；已替换为“确认后才提交、云盘后端重新鉴权”的真实语义，并增加 Android 单测防止回退。
- 真机首次英文输入 `Create a folder named ...` 被现有正则错误抽取为文件夹名 `a`；该计划已取消且未执行。这是阶段 8 的识别规则回归项，不属于执行分离链路故障。

### 阶段 6：批量、组合与客户端输入

状态：**代码、H2/Flyway、Cloud 动作、执行状态机、Android 单元测试、本地完整回归、Compose 校验和 Android 真机组合链路验收均已完成；生产开关保持关闭。**

变更：

- 增加批量删除、批量移动、批量重命名。
- 执行前重新生成或验证影响范围指纹。
- 增加组合步骤和 `WAITING_CLIENT_INPUT`。
- 上传仍由客户端传输字节，云端管理整体工作流状态。

验收门槛：

- 批量影响数量和确认快照不一致时拒绝执行。
- 部分成功有逐项结果和明确终态。
- 组合步骤引用只能使用前一步白名单输出。
- 客户端输入超时后任务进入 `EXPIRED`，不悬挂。

2026-09-28 真机组合链路验收：

- Android 13 真机通过 ADB reverse 连接本地 CloudStorageApi、RAG 与 `ragExecution`；Identity 使用一次性本机测试桩，只为测试用户返回 `RAG_ADMIN`/`CLOUD_ADMIN`，未修改生产账号、生产配置或仓库文件。
- 中文请求“在 Documents 下新建文件夹然后上传文件”生成 `FOLDER_CREATE -> UPLOAD_FILES` 两步执行 `f6e85cf0-6898-4899-ae59-e4042db87361`。用户点击“确认执行”后，服务端先创建文件夹，任务与上传步骤进入 `WAITING_CLIENT_INPUT`，随后客户端才打开系统文件选择器。
- 上传 50 字节测试文件后，CloudStorageApi 通过固定 HMAC 通道复核文件节点归属、类型和父目录；任务最终为 `SUCCEEDED`、版本 `7`，两个步骤均为 `SUCCEEDED` 且 `attempts=1`，Cloud 动作回执分别为 `FOLDER_CREATED` 和 `UPLOADS_VERIFIED`。
- 事件顺序为 `EXECUTION_REGISTERED -> EXECUTION_CONFIRMED -> EXECUTION_STARTED -> STEP_SUCCEEDED -> EXECUTION_STEP_QUEUED -> STEP_WAITING_CLIENT_INPUT -> EXECUTION_WAITING_CLIENT_INPUT -> STEP_SUCCEEDED -> CLIENT_INPUT_COMPLETED -> EXECUTION_SUCCEEDED`，证明客户端输入阶段和服务端工作流状态一致闭环。
- 真机回归发现复合上传计划在已有有效 `executionReference` 时仍优先显示旧客户端上传入口，会绕过云端确认。映射规则已改为云端引用优先，并增加 Android 回归测试；只有进入 `WAITING_CLIENT_INPUT` 后才允许启动本地文件选择与上传。
- 联调收尾发现保存的本地回环地址可能跨构建残留。客户端配置迁移现为双向隔离：本地调试构建使用显式本地默认地址，正式地址构建忽略残留的 `127.0.0.1`/`localhost`/`10.0.2.2` 地址；Debug 与 Release 安全包均验证为正式地址且两个执行开关关闭。
- 验收生成的文件夹和文件节点已按精确 ID 删除，设备测试文件已移除，对应 COS 对象清理任务由正常调度完成；执行、步骤、事件和动作回执作为本地审计证据保留，未清理数据库卷或其他数据。

### 阶段 7：清理旧执行路径

变更：

- 删除客户端本地写操作执行器及其网络适配代码。
- `backendActionDraft` 中的 method/path/body 停止对普通客户端输出。
- 更新 RAG action bridge、移动端契约和历史文档。
- 保留必要的只读兼容字段一个明确版本窗口。

验收门槛：

- 仓库中不存在启用本地 RAG 写执行的运行路径。
- 所有 RAG 写动作只能进入 `ragExecution`。
- 旧客户端收到不可执行提示，而不是回退到本地执行。
- 完成平台级回归、Compose 解析和生产灰度验收。

### 阶段 8：识别能力优化

只有阶段 7 稳定验收后，才开始调整：

- 意图规则与仲裁。
- DeepSeek Prompt 和模型配置。
- 语料检索与评测集。
- 否定、取消、复合语句、多轮指代和口语化能力。

识别优化不得绕过新的动作契约和确认状态机。

## 16. 发布与回滚

### 16.1 功能开关

建议至少提供：

```text
ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED=false
ALICIA_RAG_EXECUTION_WORKER_ENABLED=false
ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED=false
ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=
ALICIA_RAG_EXECUTION_ADMIN_ONLY=true
```

开关默认关闭。生产开启顺序：

```text
服务与数据库
-> 影子登记
-> 内部幂等执行验证
-> 管理员单动作
-> 小比例普通用户
-> 全量单动作
-> 批量动作
-> 组合动作
```

### 16.2 回滚原则

- 关闭 Worker 后不领取新任务。
- `RUNNING` 任务先查询 CloudStorageApi 回执，不直接标记失败。
- `QUEUED` 任务保留，修复后继续或由用户取消。
- 回滚不能自动重新启用客户端本地执行器。
- 数据库迁移只向前修复，不执行破坏性降级脚本。
- 普通 CloudStorageApi 业务接口和客户端手工操作始终可用。

### 16.3 分阶段生产入口

生产发布工具已在 2026-09-28 补齐，但尚未执行生产切换：

- `prepare-rag-execution-production-env.sh` 只生成 mode `600` 的候选 `.env`，不会覆盖当前配置，也不会打印密钥。
- `verify-rag-execution-production.sh` 同时支持候选预检和运行态验收；运行态检查特性状态、依赖健康、公网路由、内部路由封锁、容器健康和重启次数。
- `update-rag-execution-production.sh` 默认只预演。只有显式传入 `--apply` 才会先备份，再重建 `api`、`rag`、`rag-execution` 和 `frontend`；验收失败默认停止 `rag-execution`，CloudStorageApi 和手工云盘操作继续可用。
- `backup-production-data.sh` 已把独立 `alicia_rag_execution` 数据库纳入备份和 manifest；数据库尚未创建时会明确记录并跳过，而不是伪造成功 dump。

三个可执行阶段固定为：

```text
foundation    服务、独立数据库和公网健康入口就绪，所有执行能力关闭
shadow        开启总开关、内部登记和 RAG 影子登记，不开放确认、不启动 Worker
admin-single  仅 RAG_ADMIN、仅一个显式单动作、开启确认/Worker/Cloud 分派
```

批量、组合和普通用户灰度不属于这三个首轮阶段。详细命令、观察项和回退条件以 [生产灰度运行手册](PRODUCTION_ROLLOUT_RUNBOOK.md) 为准。

## 17. 测试矩阵

### 17.1 单元测试

- 所有合法和非法状态迁移。
- 动作载荷校验。
- plan hash 与 request hash。
- 权限、过期和版本冲突。
- 重试分类。
- 脱敏日志与错误映射。

### 17.2 持久化与并发测试

- 同一任务并发确认。
- 多 Worker 抢占同一任务。
- 租约过期与恢复。
- 乐观锁冲突。
- 幂等键相同、载荷相同/不同。
- 数据库重启后的恢复。

### 17.3 服务契约测试

- RAG -> ragExecution 计划登记。
- ragExecution -> CloudStorageApi 动作信封。
- 客户端 -> ragExecution 确认、取消、查询和 SSE。
- 合约版本不兼容时明确拒绝。

### 17.4 安全测试

- 伪造服务签名。
- 过期时间戳和 nonce 重放。
- 用户越权访问任务。
- actor 与资源所有者不一致。
- 任意 URL、未知动作和内部字段注入。
- 超大步骤数、超大批次和超大 JSON。
- 日志中不存在 Token、密钥和敏感字段。

### 17.5 故障测试

- CloudStorageApi 连接超时。
- CloudStorageApi 已执行但响应丢失。
- Worker 执行中被终止。
- 数据库短暂不可用。
- SSE 断线重连。
- 确认后资源被用户手工修改。

### 17.6 端到端验收

至少覆盖：

```text
重命名单文件
删除到回收站
移动单节点
创建文件夹
创建分享链接
取消待确认任务
任务自动过期
重复确认
批量移动/删除
客户端退出后完成
服务重启后恢复
跨用户访问拒绝
上传等待客户端输入
```

## 18. 完成定义

执行分离只有满足以下全部条件才算完成：

- `ragExecution` 是 RAG 写操作确认与编排的唯一服务端入口。
- `rag` 不执行写操作，也不输出可由普通客户端自由执行的 URL 草稿。
- 客户端不再包含云端写操作调度器。
- CloudStorageApi 继续是云盘业务唯一执行所有者。
- 所有动作有类型化契约、幂等回执和权限复核。
- 任务重启可恢复，重复确认不会重复执行。
- 任务有明确终态、过期策略和审计事件。
- 内部入口不暴露公网，服务密钥和用户 Token 不进入日志或数据库。
- 单动作、批量动作和客户端输入路径通过端到端验收。
- 旧执行路径已删除，不存在长期双写或隐式回退。
- README、运行手册、发布脚本和完成日志与最终实现一致。
- 两个仓库保持既定 Identity 与 Cloud 所有权边界。

## 19. 推荐提交切片

每个提交只完成一个可验证主题：

1. `Document RAG execution service boundaries`
2. `Scaffold durable RAG execution service`
3. `Add RAG execution state machine and persistence`
4. `Register RAG plans in shadow mode`
5. `Add idempotent Cloud action dispatch contract`
6. `Execute atomic RAG actions behind admin gate`
7. `Move client confirmation to RAG execution service`
8. `Add batch action execution and recovery`
9. `Add client-input workflow for uploads`
10. `Remove legacy client-side RAG action execution`
11. `Document RAG execution migration completion`

禁止把脚手架、数据库、CloudStorageApi 内部协议、客户端切换和生产开关一次性塞进一个大提交。

## 20. 文档维护规则

当前文档各自只承担一种职责：

```text
ragExecution/README.md                                  模块入口与当前阶段
ragExecution/docs/RAG_EXECUTION_SEPARATION_PLAN.md      总体边界、状态机与阶段事实
ragExecution/docs/PHASE_0_ACTION_CONTRACT_BASELINE.md   改造前动作契约冻结基线
ragExecution/docs/API_CONTRACT.md                       已实现内部 API 与协议版本
```

后续只在对应能力实际进入实施或生产运行时新增：

```text
docs/API_CONTRACT.md       最终公开/内部 API 与版本
docs/RUNBOOK.md            部署、巡检、暂停 Worker、恢复和事故处理
docs/MIGRATION_LOG.md      每阶段切换与验收记录
docs/adr/*.md              发生重大不可逆设计变化时才新增
```

不要为每个小改动新增临时 Markdown。过时内容直接更新或明确归档，禁止让旧客户端执行方案和新云端执行方案同时以“当前设计”的身份存在。
