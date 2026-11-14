# RAG Execution 管理员只读可观测面完成日志

日期：2026-11-14

## 1. 结论

RAG Execution 管理员只读可观测面已完成实现、回归、受控生产发布和真实角色门禁验收。该能力用于查看
任务总体状态、按状态或 owner 查询任务，以及检查单个任务的步骤和公开事件顺序，不提供确认、取消、
重试、删除或任何业务写入入口。

本节点不重新启动独立识别优化，不修改 RAG 识别行为，不新增数据库迁移，也不扩大生产执行边界。
生产继续保持：

```text
stage          admin-single
role           RAG_ADMIN
allowed action FOLDER_CREATE
```

## 2. API 边界

新增三个 GET-only 管理端点：

```text
GET /api/admin/executions/overview
GET /api/admin/executions
GET /api/admin/executions/{executionId}
```

所有入口同时要求：

- RAG Execution 总开关已启用；
- 有效 Identity Authorization；
- 显式 `appRoles.rag=RAG_ADMIN`，`RAG_USER` 不可访问；
- 列表分页为 1-based，单页最大 100；
- 查询事务全部为 `readOnly=true`。

返回内容只包含任务状态、风险、动作类型、尝试次数、安全结果/错误码、步骤状态和公开事件序号。
明确不返回原始对话、模型响应、动作摘要、计划哈希、幂等键、步骤 payload/result 或事件 payload。

## 3. 管理台边界

`sysManage` 新增 `/rag-executions` 只读视图：

- `RAG_ADMIN` 可以查看概览、分页列表和脱敏详情；
- 只有 Cloud 管理权限的账号不能查看 RAG 视图；
- 只有 `RAG_ADMIN` 的账号可以进入管理台，但不会看到云盘用户、运营或 APK 页面；
- 同时具备两类权限的账号按视图分别授权；
- 页面没有确认、取消、重试、删除或其他执行控制按钮；
- 重复加载由同步 ref 拦截，过期请求按 token、角色和 request ID 丢弃。

公网仍复用现有 `/rag-execution/api/**` 反向代理，内部登记和 Cloud 动作路由继续被 Nginx 屏蔽。

## 4. 契约与回归

新增跨层静态契约，构建时同时检查后端控制器、服务、响应字段和前端调用：

```text
RAG Execution tests                         59/59
管理员 API 集成测试                          3/3
sysManage production build                  通过
sysManage CloudStorageApi contracts          通过
sysManage RAG Execution contracts            通过
PowerShell frontend split --SkipBuild        通过
Bash frontend console boundaries             通过
最大 sysManage JavaScript chunk              444.0 KiB / 500 KiB
```

Bash 边界脚本同时修复了 Windows Git Bash 下以 `/` 开头的 `rg` 正则被 MSYS 转换成磁盘路径的问题；
Linux 行为不变。

## 5. 发布边界

2026-11-14 已按批准范围成对更新 `rag-execution` 与 `frontend`，发布前后分别验证：

- 生产 stage、角色和动作白名单没有变化；
- 三个管理端点对 `RAG_ADMIN` 可读、对 `RAG_USER` 返回 403；
- 管理台没有写操作控件；
- 既有任务、回执、事件序列和 Cloud/RAG 依赖健康；
- 容器重启、异常日志和公开 Android 版本没有非预期变化。

本次发布使用受控的窄范围服务集，避免无关重建 `api` 与 `rag`：

```bash
bash deploy/scripts/update-rag-execution-production.sh \
  --stage admin-single \
  --action FOLDER_CREATE \
  --service-set rag-execution-frontend
```

上述命令先完成 dry-run，在生产预检通过并获得明确发布批准后追加 `--apply`。该模式仍执行生产备份、
完整 RAG Execution 验收和失败安全停止，但通过 `--no-deps` 只重建 `rag-execution` 与 `frontend`。

## 6. 生产只读预检

2026-11-14 发布前只读预检通过，未拉取代码、未构建镜像、未重启容器：

```text
production commit              0a03d0e
target before script hardening 65b0440
tracked server state           clean
stage                          admin-single
role boundary                  RAG_ADMIN
allowed action                 FOLDER_CREATE
rag-execution health/restarts  healthy / 0
non-terminal executions        0
failed or partial executions   0
stuck running executions       0
event sequence gaps            0
missing/mismatched receipts    0 / 0
cancelled/expired receipts     0
active rag-active-* nodes      0
public Android version         0.1.14
recent service anomaly logs    no matches
```

生产当前访问新增 overview 端点返回 404，符合“尚未部署”的基线。服务器待拉取范围除本能力外只有一笔纯文档提交，
没有夹带其他运行功能。预检同时发现原更新入口默认会重建四个服务，因此在发布前增加了枚举式窄范围服务集；
默认 `full` 行为保持不变，非法或任意服务集会被拒绝。

## 7. 生产发布结果

受控发布在提交 `a169030` 上完成：

```text
deployment commit               a169030
service set                     rag-execution-frontend
backup                          deploy/generated/production-backups/20261114071557
rag-execution started           2026-11-14T07:19:13Z
frontend started                2026-11-14T07:19:13Z
rag-execution health/restarts   healthy / 0
frontend restarts               0
api started/restarts            2026-11-14T05:50:25Z / 0
rag started/restarts            2026-11-14T05:40:34Z / 0
tracked server state            clean
public Android version          0.1.14
```

`api` 与 `rag` 的启动时间保持不变，证明窄范围发布没有重建无关服务。生产备份包含 Cloud、Identity、
RAG Execution 数据库以及敏感配置归档、校验和与清单，目录权限为 `700`。最终生产验证再次确认：

```text
stage                          admin-single
role boundary                  RAG_ADMIN
allowed action                 FOLDER_CREATE
Compose configuration          passed
public health                  reachable
internal registration routes   blocked
internal cloud action routes   blocked
required dependencies          healthy
recent target anomaly logs     no matches
```

发布后对六条公开路径执行 25 轮主动探测，共 150 次请求，全部返回 HTTP 200。管理台
`/console/cloud/rag-executions` 返回 HTTP 200，生产前端产物包含“RAG 执行任务”视图标记。

## 8. 生产角色与脱敏验收

使用 Android 0.1.14 中的真实登录态完成正反角色验证；访问令牌只在探针进程内存中使用，未写入磁盘、
未打印或提交。临时探针在验证后立即删除。

`RAG_ADMIN` 正向结果：

```text
RAG access role                RAG_ADMIN
overview                       HTTP 200
total executions               82
active executions              0
attention executions           0
retry-wait executions          0
retried steps                  1
list                           HTTP 200, page 1, size 20
detail                         HTTP 200, 1 step, 2 public events
detail event order             strictly ascending
forbidden response fields      0
```

脱敏检查递归覆盖 overview、list 和 detail 响应，确认没有对话 ID、来源响应 ID、计划 ID/哈希、幂等键、
动作摘要、步骤 payload/result、事件公开 payload 或内部错误正文。

`RAG_USER` 负向结果：

```text
RAG access role                RAG_USER
overview                       HTTP 403
list                           HTTP 403
detail                         HTTP 403
safe error code                rag_admin_observability_required
```

匿名访问三个管理入口返回 HTTP 401。角色验收只执行 GET 请求，没有创建、确认、取消、重试或修改任务，
也没有产生 Cloud 业务写入。

## 9. 发布后数据不变量

生产数据库和运行态复核结果：

```text
non-terminal executions        0
failed or partial executions   0
stuck running executions       0
event sequence gaps            0
receipt/step mismatches         0
missing folder receipts        0
cancelled/expired receipts     0
active rag-active-* nodes      0
```

最终判断：管理员只读可观测面已完成生产闭环，可以作为后续 RAG Execution 阶段推进的稳定观测基线；
本节点没有扩大角色、动作白名单或执行能力。镜像构建期间 npm audit 仍报告既有的 2 个 high severity
依赖告警，本次生产窗口未夹带依赖升级；应在独立维护节点完成依赖定位、兼容性回归和修复。
