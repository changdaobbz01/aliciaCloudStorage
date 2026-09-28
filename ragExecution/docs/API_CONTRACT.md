# RAG 执行服务 API 契约

## 1. 适用范围

本文档定义阶段 2 已实现的 RAG 到 `ragExecution` 内部计划登记协议、阶段 3 已实现的 `ragExecution` 到 CloudStorageApi 受控动作协议、阶段 4 已实现的用户确认、查询、事件订阅和 Worker 分派协议、阶段 5 已实现的 Android 客户端调用边界，以及阶段 6 已实现的批量动作、顺序组合和客户端上传输入协议。总体边界、状态机和后续阶段以 [RAG 执行服务分离实施方案](RAG_EXECUTION_SEPARATION_PLAN.md) 为准。

上述能力都由独立开关保护且默认关闭。代码已具备管理员单动作灰度和 Android 云端确认能力，但生产客户端开关与生产流量均未启用。

## 2. 端点与开关

```text
POST /internal/executions
```

端点只有在以下两个执行服务开关同时为 `true` 时存在，否则返回 `404`：

```text
ALICIA_RAG_EXECUTION_ENABLED
ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED
```

RAG 侧影子登记另由下列开关独立控制：

```text
ALICIA_RAG_EXECUTION_SHADOW_REGISTRATION_ENABLED
```

三个开关默认均为 `false`。服务密钥必须为独立随机值且不少于 32 个字符，不能与用户 Token、数据库密码或其他 webhook 密钥复用。

安全启用顺序：

1. 启动执行服务并保持三个开关关闭，确认数据库健康。
2. 配置两端相同的专用服务密钥和正确的 Identity 地址。
3. 开启执行服务总开关与登记开关，确认数据库和 Identity 依赖健康。
4. 最后开启 RAG 影子登记。

回退时先关闭 RAG 影子登记，再关闭执行服务登记开关。影子登记失败本身不会改变现有 RAG 对话响应。

## 3. 双层身份校验

每个请求必须同时通过：

- 服务身份：HMAC-SHA256 请求签名。
- 用户身份：原始 `Authorization` 请求头由执行服务独立调用 Identity `/api/identity/auth/me` 校验，并以 Identity 返回的用户 ID 作为任务所有者。

执行服务不得持久化 Access Token 或 Refresh Token。RAG 自报的用户 ID 不属于登记契约，执行服务也不接受它作为所有权依据。

## 4. 服务签名

请求必须携带：

```text
X-Alicia-Caller: rag
X-Alicia-Timestamp: <Unix epoch seconds>
X-Alicia-Nonce: <16..64 characters>
X-Alicia-Content-SHA256: <lowercase SHA-256 of exact HTTP body bytes>
X-Alicia-Signature: <lowercase HMAC-SHA256>
```

签名原文以换行拼接，末尾不增加换行：

```text
POST
/internal/executions
rag
<timestamp>
<nonce>
<body-sha256>
```

签名使用 UTF-8 编码和专用服务密钥。服务端按常量时间比较摘要与签名，时间默认只允许正负 60 秒偏差；已消费 nonce 写入独立数据库表，在有效期内重放返回 `401`。签名必须覆盖最终发送的精确请求体字节，禁止签名后再次序列化请求。

## 5. 请求契约

顶层结构：

```json
{
  "planSchemaVersion": "action_plan_v2",
  "sourceResponseId": "response-id",
  "conversationId": "conversation-id",
  "intentId": "intent-id",
  "planId": "plan-id",
  "planHash": "64-char-lowercase-sha256",
  "risk": "LOW|MEDIUM|HIGH",
  "summary": "safe confirmation summary",
  "steps": [
    {
      "stepKey": "rename_node",
      "actionType": "NODE_RENAME",
      "payloadSchemaVersion": "rag_execution_action_v1",
      "payload": {
        "nodeId": 123,
        "newName": "example.txt"
      },
      "dependsOn": [],
      "outputKey": "",
      "requiredClientFields": []
    }
  ],
  "expiresAt": "2026-09-18T09:00:00Z"
}
```

当前计划登记协议只接受以下类型化动作：

```text
NODE_RENAME
NODE_TRASH
NODE_MOVE
FOLDER_CREATE
SHARE_CREATE
NODE_BATCH_TRASH
NODE_BATCH_MOVE
NODE_BATCH_RENAME
UPLOAD_FILES
```

载荷必须严格匹配对应 Java record；未知字段会被拒绝。任何 URL、URI、HTTP 方法、路径、主机、存储 key、Authorization 或 Token 字段都会被拒绝，文本中的 `http://` 和 `https://` 同样不允许。默认限制为 10 个步骤、64 KiB 计划体和 10 分钟确认有效期。

## 6. 计划 hash 与幂等

`planHash` 是以下字段组成的 canonical JSON 的 SHA-256 小写十六进制值：

```text
planSchemaVersion
planId
intentId
risk
summary
steps[stepKey, actionType, payloadSchemaVersion, payload, dependsOn, outputKey, requiredClientFields]
```

canonical JSON 规则：对象键递归按字典序排序，数组顺序保持不变，使用 UTF-8 紧凑 JSON。`sourceResponseId`、`conversationId` 和 `expiresAt` 不进入 hash。

登记幂等边界是 Identity 用户、`planId` 与 `planHash`。相同用户、计划和 hash 的重复登记返回同一任务；相同计划 ID 但内容 hash 不同必须拒绝，不能覆盖已登记任务。

## 7. 成功响应

```json
{
  "executionId": "uuid",
  "status": "PENDING_CONFIRMATION",
  "version": 0,
  "expiresAt": "2026-09-18T09:00:00Z"
}
```

RAG 仅把以上四个字段作为只读 `executionReference` 附加到同步或 SSE 最终计划响应。Android 阶段 5 客户端使用其中的 `executionId` 与 `version` 调用公开确认和查询接口；生产 BuildConfig 开关默认关闭。

Android 客户端约束：

- 确认只发送路径中的 `executionId` 和正文 `expectedVersion`，不发送 ActionPlan、method、path 或业务 body。
- 查询始终使用最初确认的 `executionId`；响应任务 ID 不一致时停止自动刷新，不能转而跟踪其他任务。
- 非 `ApiException` 的确认响应丢失可查询同一任务恢复；任务仍为 `PENDING_CONFIRMATION` 时不得假定确认成功。
- 一旦云端模式开启，缺失 `executionReference`、通道关闭或调用失败都不得自动回退旧本地执行器；云端模式关闭时，旧兼容开关才可能处理没有云端引用的旧响应。
- 只要响应携带有效 `executionReference`，上传和复合计划必须先展示云端“确认执行”，不得先展示或触发旧客户端上传控件；本地文件选择只能由云端任务进入 `WAITING_CLIENT_INPUT` 后发起。
- 正式地址构建必须忽略应用数据中残留的本地回环地址，避免真机联调配置污染后续安全包；本地调试地址只能由显式本地构建默认值启用。
- `ALICIA_RAG_CLOUD_EXECUTION_ENABLED` 和 Release 对应开关默认 `false`；旧 `ALICIA_RAG_ACTION_EXECUTION_ENABLED` 仅用于没有云端引用的兼容响应，也默认 `false`。

## 8. 错误与日志约束

- `401`：服务签名无效、过期、正文摘要不匹配或 nonce 重放。
- `404`：登记功能未启用。
- `409`：同一用户的 `planId` 已存在但计划内容 hash 不同，或并发登记冲突。
- `413`：计划超过大小限制。
- `422`：计划版本、hash、动作、载荷、到期时间或字段不合法。
- `401/403`：用户 Token 缺失、失效或没有 RAG 权限。
- `503`：Identity 不可用，无法独立验证用户会话。

响应只返回稳定错误码，不返回堆栈、用户 Token、密钥或底层响应正文。RAG 影子登记失败日志只记录脱敏后的 response ID、plan ID 和异常类别，不记录载荷、文件名、路径或 Authorization。

## 9. 计划登记阶段边界

阶段 2 的成功只表示计划已安全持久化为 `PENDING_CONFIRMATION`，不表示业务动作已经执行。阶段 4 Worker 只有在总开关、Worker 和 Cloud 分派开关均显式开启后才会调用阶段 3 的 CloudStorageApi 入口。

阶段 6 已完成批量快照、顺序组合、Android 上传输入桥接和 Android 真机组合链路验收，但不包含生产启用或旧路径删除。当前 Web 端没有 RAG 确认界面，没有可切换的 Web 执行调用。

## 10. CloudStorageApi 受控动作协议

### 10.1 端点与独立开关

```text
POST /internal/rag-execution/actions
```

端点只有在下列 CloudStorageApi 开关为 `true` 时注册；默认值为 `false`，关闭时框架直接返回 `404`：

```text
ALICIA_RAG_EXECUTION_CLOUD_ACTIONS_ENABLED
```

服务密钥使用 `ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET`，必须至少 32 个字符。它不能复用 RAG 到执行服务的 `ALICIA_RAG_EXECUTION_SERVICE_SECRET`，也不能复用 JWT、数据库、分享或 webhook 密钥。

### 10.2 请求信封

```json
{
  "executionId": "uuid",
  "stepId": "uuid",
  "actorUserId": 42,
  "actionType": "NODE_RENAME",
  "payloadSchemaVersion": "rag_execution_action_v1",
  "payload": {
    "nodeId": 123,
    "expectedNodeVersion": 0,
    "newName": "example.txt"
  },
  "requestHash": "64-char-lowercase-sha256",
  "issuedAt": "2026-09-18T09:00:00Z"
}
```

只允许 `NODE_RENAME`、`NODE_TRASH`、`NODE_MOVE`、`FOLDER_CREATE`、`SHARE_CREATE`、三类 `NODE_BATCH_*` 与只读校验用途的 `UPLOAD_FILES`。载荷必须严格命中相应 record；未知字段、任意 URL/URI、HTTP method/path/host、存储 key、Authorization、Token、Password 以及文本 URL 均拒绝。`UPLOAD_FILES` 不上传字节也不创建节点，只验证客户端回报的节点均属于 actor、仍有效、类型为文件且位于预期父目录。`expectedNodeVersion` 可省略，提供时必须是非负整数，初始 JPA 版本为 `0`。`SHARE_CREATE` 当前只接受无密码分享，有效期必须为 1-365 天；带密码分享在专用秘密输入或加密协议落地前不进入任务池。

### 10.3 服务签名

请求头：

```text
X-Alicia-Caller: rag-execution
X-Alicia-Timestamp: <Unix epoch seconds>
X-Alicia-Nonce: <16..64 characters>
X-Alicia-Content-SHA256: <lowercase SHA-256 of exact HTTP body bytes>
X-Alicia-Signature: <lowercase HMAC-SHA256>
```

签名原文：

```text
POST
/internal/rag-execution/actions
rag-execution
<timestamp>
<nonce>
<body-sha256>
```

时间偏差默认不超过正负 60 秒，nonce 默认持久化 3 分钟。请求体默认上限为 64 KiB。签名、正文摘要和 nonce 均验证成功后才进入业务分派。

### 10.4 requestHash 与幂等

`requestHash` 是以下字段组成的递归键排序紧凑 JSON 的 SHA-256；`issuedAt` 不进入 hash，因此安全重试可以刷新签名时间和 nonce 而保持同一业务请求身份：

```text
executionId
stepId
actorUserId
actionType
payloadSchemaVersion
payload
```

CloudStorageApi 以 `stepId` 为动作收据主键，并再次比较 `executionId`、actor、动作类型、schema 与 `requestHash`：

- 精确重复返回原成功收据，不再次调用业务 Service。
- 同一步骤但内容变化返回 `409 step_request_conflict`。
- 并发重复由数据库唯一约束裁决；胜者提交业务事务和收据，其他请求读取并返回同一收据。
- 业务动作与 `SUCCEEDED` 收据在同一事务中提交，不产生“业务已改但无成功回执”的窗口。

### 10.5 业务授权与写边界

服务签名只证明调用者是 `ragExecution`，不能替代用户授权。CloudStorageApi 必须按 `actorUserId` 重新校验节点存在、归属、未删除、目标目录类型和可选实体版本，再把类型化载荷转换为现有 DTO，并只调用 `StorageCommandService` 或 `ShareLinkService` 完成写入。`ragexecution` 包不得直接通过业务 Repository 修改云盘实体。

### 10.6 成功响应与稳定错误

成功响应示例：

```json
{
  "executionId": "uuid",
  "stepId": "uuid",
  "status": "SUCCEEDED",
  "resultCode": "NODE_RENAMED",
  "result": {
    "nodeId": 123,
    "entityVersion": 1,
    "parentId": null,
    "nodeType": "FILE"
  },
  "completedAt": "2026-09-18T09:00:01Z"
}
```

- `401`：服务身份、时间、正文摘要、签名或 nonce 无效。
- `403`：actor 不是资源所有者。
- `404`：功能关闭或资源不存在。
- `409`：step 内容冲突、执行中、资源状态/版本冲突或业务唯一约束冲突。
- `413`：请求体超过上限。
- `422`：信封、动作、schema、hash 或类型化载荷无效。

响应和日志不得包含文件名、完整 payload、签名、密钥或用户 Token。该路径只允许容器内部访问；`webApp/nginx/default.conf` 对 `/internal/rag-execution/**` 显式返回 `404`。

### 10.7 阶段 4 调用边界

阶段 4 Worker 只调用固定的 `/internal/rag-execution/actions`，不能接受计划中的 URL、HTTP method 或 path。`ALICIA_RAG_EXECUTION_WORKER_ENABLED` 与 `ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED` 默认均为 `false`；只有显式开启后才会自动领取和分派，客户端切换仍不在本阶段。

## 11. 公开确认、查询与事件协议

### 11.1 路由与身份

容器内路由如下；公网 Nginx 使用 `/rag-execution` 前缀转发，因此公网形式为 `/rag-execution/api/executions/**`：

```text
GET  /api/executions/{executionId}
POST /api/executions/{executionId}/confirm
POST /api/executions/{executionId}/cancel
POST /api/executions/{executionId}/client-input
GET  /api/executions/{executionId}/events?after=<sequence>
GET  /api/executions/{executionId}/stream?after=<sequence>
```

所有请求必须携带用户 `Authorization`。执行服务每次独立调用 Identity 验证当前用户和 RAG 应用角色，并按登记时的 owner ID 查询；非所有者统一得到 `404 execution_not_found`，不能据此枚举其他用户任务。查询、取消和事件订阅要求 `ALICIA_RAG_EXECUTION_ENABLED=true`；确认还要求 `ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED=true`。

### 11.2 确认和取消

确认请求必须携带登记响应中的乐观版本：

```json
{
  "expectedVersion": 0
}
```

阶段 4 规则：

- `ALICIA_RAG_EXECUTION_ADMIN_ONLY` 必须保持 `true`，仅 `RAG_ADMIN` 可确认。
- `ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS` 必须显式且非空；确认和 Worker 会分别复核动作是否在 allowlist 中。
- 一个任务最多 10 个有序步骤；依赖只能指向前置步骤，输出引用只允许显式白名单字段，当前上传组合仅允许引用 `FOLDER_CREATE.nodeId`。
- `SHARE_CREATE` 当前只允许一个节点。
- 只有未过期的 `PENDING_CONFIRMATION` 可首次确认，成功后原子进入 `QUEUED` 并把排队期限收紧到默认 2 分钟。
- 使用原 `expectedVersion` 重复确认已经排队、执行、等待重试或成功的同一任务，会返回当前状态而不重复入队。
- 取消仅适用于 `PENDING_CONFIRMATION`、`QUEUED` 或 `WAITING_CLIENT_INPUT`；运行中和终态不接受取消。

公开响应不返回动作 payload、用户 Token、服务密钥或内部错误正文。步骤只暴露动作类型、状态、尝试次数和安全结果。

### 11.3 事件和 SSE

事件使用每个任务内单调递增的 `sequence`。轮询接口用 `after` 读取严格大于该序号的事件；SSE 同时支持 `after` 和标准 `Last-Event-ID`，取两者较大值恢复。服务会发送心跳，并在任务进入终态后关闭流。公开事件 payload 仅含状态、版本、步骤 ID、动作类型、尝试次数等安全字段，不回传动作 payload。

## 12. Worker、开关与恢复语义

### 12.1 功能开关

```text
ALICIA_RAG_EXECUTION_ENABLED=false
ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED=false
ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED=false
ALICIA_RAG_EXECUTION_WORKER_ENABLED=false
ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED=false
ALICIA_RAG_EXECUTION_ADMIN_ONLY=true
ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=
```

所有执行能力默认关闭。子功能不能在总开关关闭时开启；公开确认必须是管理员模式并配置非空 allowlist；Cloud 分派依赖 Worker，并要求单独配置至少 32 字符的 Cloud 服务密钥。数据库和服务先就绪，再依次开启登记、RAG 影子登记、CloudStorageApi 内部动作入口、管理员确认、Worker，最后才开启 Cloud 分派。回退时首先关闭 Cloud 分派或 Worker，以停止领取新任务；已有记录和终态回执保留。

### 12.2 领取、重试与恢复

Worker 使用数据库行锁和 `SKIP LOCKED` 领取任务，并写入有期限的 lease；过期 lease 可被其他 Worker 恢复。可重试的网络错误、`429`、`5xx` 和处理中冲突按 2 秒、10 秒、30 秒退避，默认最多 3 次。每次安全重试复用相同 `executionId`、`stepId` 和 `requestHash`，但刷新签名时间和 nonce。

如果 CloudStorageApi 已提交业务事务但响应丢失，下一次请求会以同一 `stepId` 读取原成功收据，不再次执行业务变更。Worker 或服务重启时，`RUNNING` 步骤保持原尝试身份并使用同一回执恢复路径，不能生成新幂等键盲目重放。

### 12.3 当前发布边界

阶段 4 已通过本地测试和真实 MySQL 8 隔离容器端到端验证，包括首次 Cloud 响应丢失后的安全收敛。阶段 5 已完成 Android 云端确认真机验收。阶段 6 已完成批量快照校验、顺序步骤、`WAITING_CLIENT_INPUT`、15 分钟默认输入超时、Android 文件字节上传桥接、Cloud 端上传结果复核和 Android 真机组合链路验收。生产开关仍全部默认关闭；旧执行器在一个兼容发布周期内保留且默认关闭，必须等生产灰度稳定后才能进入阶段 7 删除。

生产配置只能按 `foundation -> shadow -> admin-single` 单向推进。候选配置必须先通过 `verify-rag-execution-production.sh --preflight`；运行态必须同时验证特性状态、数据库/Identity/Cloud 依赖、公网健康、两个内部路由族 404、容器健康和重启次数。`admin-single` 只接受一个非批量服务端动作，首选 `FOLDER_CREATE`，且继续强制 `RAG_ADMIN`。

## 13. 阶段 6 批量与客户端输入协议

- 批量动作携带完整节点快照、数量和 canonical SHA-256 指纹；CloudStorageApi 在事务写入前重新加载 actor 当前节点并复算，任一缺失、越权或变更都返回 `409 batch_snapshot_stale`。
- `FOLDER_CREATE -> UPLOAD_FILES` 是当前唯一开放组合。上传步骤声明 `dependsOn=["create_folder"]`、`requiredClientFields=["files"]`，只允许引用前一步 `nodeId`。
- Worker 完成文件夹创建后，把解析出的 `parentId` 和 deadline 写入步骤安全结果，并将任务置为 `WAITING_CLIENT_INPUT`；客户端看不到内部动作 payload。
- 客户端只向 CloudStorageApi 上传本地文件字节，再调用 `POST /api/executions/{executionId}/client-input` 回报步骤 ID、乐观版本、状态和节点 ID。执行服务通过固定 HMAC Cloud 动作通道复核节点归属、类型和父目录后才接受成功。
- 客户端取消或上传失败时，已经成功的服务端步骤不自动补偿，任务进入 `PARTIALLY_SUCCEEDED`；超时进入 `EXPIRED`。单个批量业务动作仍保持原子语义，不用 `PARTIALLY_SUCCEEDED` 表示半提交。
