# Alicia RAG Execution

`ragExecution` 是 Alicia 云盘的 RAG 执行编排服务目录。

当前状态：**阶段 0 至阶段 6 已完成代码、自动化回归和 Android 真机端到端验收；生产候选配置、只读验收和显式发布入口已经补齐。旧本地兼容路径及所有客户端、服务端开关继续默认关闭，尚未切换生产流量。**

本服务未来只负责：

- 保存 RAG 生成的受控执行计划。
- 管理确认、取消、过期、排队、执行和结果状态。
- 编排经过白名单约束的 CloudStorageApi 动作。
- 提供幂等、重试、恢复、审计和进度查询能力。

本服务不负责：

- 自然语言和意图识别。
- 直接访问或修改云盘业务表。
- 复制 CloudStorageApi 的文件业务规则。
- 接受模型生成的任意 URL、HTTP 方法或请求体。
- 保存用户 Access Token 或 Refresh Token。

CloudStorageApi 仍是文件、目录和分享业务的唯一执行所有者；`rag` 仍是语义识别与计划生成所有者；客户端最终只负责确认、取消、展示状态和提供本地文件输入。

实施前必须阅读：

- [RAG 执行服务分离实施方案](docs/RAG_EXECUTION_SEPARATION_PLAN.md)
- [阶段 0 动作契约基线](docs/PHASE_0_ACTION_CONTRACT_BASELINE.md)
- [内部计划登记 API 契约](docs/API_CONTRACT.md)

当前已建立：

- 独立 Spring Boot 模块、Docker 镜像和 `127.0.0.1:8094` 回环入口。
- 独立 `alicia_rag_execution` 数据库、最小权限账号，以及 Flyway V1-V4 执行、nonce、计划不可变和组合步骤元数据迁移。
- 集中状态机、JPA 乐观锁、数据库任务租约基础和重启持久性验证。
- 单节点、批量和客户端上传复核均使用类型化载荷，不接受任意 URL、HTTP 方法或无边界 Map。
- RAG 到执行服务的 HMAC-SHA256 内部登记、持久化防重放、Identity 独立验权与计划幂等。
- RAG 同步与 SSE 计划响应可返回只读 `executionReference`；登记失败保持原响应不变。
- CloudStorageApi 固定内部动作入口、独立 HMAC 密钥、持久化 nonce、严格类型化载荷、资源所有权/状态/版本校验和事务内幂等回执。
- 同一 `stepId` 的精确重试和并发重复只执行一次；相同 step 内容变化稳定拒绝，公网 Nginx 显式屏蔽内部路径。
- 用户只能查询、取消和订阅自己的任务；确认强制 `RAG_ADMIN` 和显式动作 allowlist，多步骤只允许向前依赖与白名单输出引用。
- 数据库租约 Worker 支持领取、续租、过期回收、重试调度和重启恢复；模糊失败重用同一 `stepId` 与 `requestHash`，由 Cloud 回执收敛。
- `/api/health` 与 `/api/health/dependencies`；Identity 在执行总开关开启时检查，CloudStorageApi 仅在真实分派开启时检查。
- Nginx 对外提供 `/rag-execution/api/**`，并同时屏蔽 `/internal/rag-execution/**` 与 `/rag-execution/internal/**`。
- Android 使用类型化客户端确认、查询和取消任务；确认只提交 `executionId` 与 `expectedVersion`，状态轮询始终绑定最初任务 ID。
- 云端任务引用优先级高于旧 `backendActionDraft`，新通道关闭或失败时不会静默回退到本地写操作。
- 客户端退出不影响已确认任务；本地上传、页面导航、预览和系统分享继续留在客户端。
- `FOLDER_CREATE -> UPLOAD_FILES` 中只有本地文件字节传输留在客户端；上传节点必须经 CloudStorageApi 按 owner、类型和父目录复核后才完成工作流。
- Android 真机已验证 `FOLDER_CREATE`：用户确认后立即强制停止客户端，服务端任务仍独立完成；执行记录、单步结果、Cloud 动作回执和实际目录节点一致，单步仅执行一次。
- Android 真机已验证 `FOLDER_CREATE -> UPLOAD_FILES`：云端先确认并创建目录，任务进入 `WAITING_CLIENT_INPUT` 后才启动本地文件选择，上传节点经 CloudStorageApi 复核后任务进入 `SUCCEEDED`。
- 真机使用的实际生产 Identity 账号不是 `RAG_ADMIN` 时，确认接口按设计返回 403；正向链路使用一次性本机 Identity 测试桩验证，没有修改生产角色、账号或数据。

本地验证：

```powershell
.\mvnw.cmd -pl ragExecution test
docker compose --profile rag-execution-foundation config
```

Compose 中的新服务受 `rag-execution-foundation` profile 保护，不会随现有默认发布命令启动。需要启动本地基础服务时，先按 `.env.example` 显式配置独立数据库密码，再执行：

```powershell
docker compose --profile rag-execution-foundation up -d rag-execution
```

缺少密码时初始化脚本会拒绝 `CHANGE_ME` 占位值。Android 阶段 5 代码完成不代表生产开关已打开：当前不要开启生产客户端或删除旧兼容路径；生产环境不能沿用示例开发密码。两个内部协议使用不同专用密钥，管理员灰度的启用顺序、签名协议和回退步骤以 [API 契约](docs/API_CONTRACT.md) 为准。

生产灰度必须使用独立运行手册和脚本，不能直接手工组合 Compose 参数：

- [生产灰度运行手册](docs/PRODUCTION_ROLLOUT_RUNBOOK.md)
- `deploy/scripts/prepare-rag-execution-production-env.sh`
- `deploy/scripts/verify-rag-execution-production.sh`
- `deploy/scripts/update-rag-execution-production.sh`

候选生成和默认发布调用都不会修改生产；只有明确安装候选 `.env` 并给更新脚本传入 `--apply` 才会发生生产变更。
