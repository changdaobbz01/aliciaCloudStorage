# RAG Execution `admin-single` 生产灰度日志｜2026-10-20

## 1. 结论

生产已按既定顺序完成：

```text
foundation -> shadow -> admin-single
```

当前只允许 `RAG_ADMIN` 确认 `FOLDER_CREATE`。普通用户、其他单动作、批量动作和组合动作均未开放；旧客户端本地写执行器继续关闭。

运行提交：`fcb6478 Preserve English folder names in RAG plans`

## 2. Shadow 晋级验收

2026-10-20 使用 Android 真机发送：

```text
Create a folder named rag-shadow-fix-check-20261020-1724
```

影子任务 `07605f85-6227-4f5a-a6bf-e169505d88a7` 的结果：

- 状态停留在 `PENDING_CONFIRMATION`，版本 `0`。
- `payload_json` 精确保存完整名称，没有再错误截断为英语冠词 `a`。
- 步骤状态 `PENDING`，`attempts=0`。
- Cloud 动作回执为 `0`，同名存储节点为 `0`。
- 客户端取消仅结束本地确认流程；Shadow 阶段未开放公网确认/取消，因此影子记录按 TTL 自动过期，不会排队或写入。
- `verify-rag-execution-production.sh shadow FOLDER_CREATE` 通过；相关容器重启次数为 `0`。

Shadow 在本次晋级前已独立运行观察，没有跳过阶段。

## 3. 解析缺陷与修复

首次 `admin-single` 探针暴露纯英文句式 `Create a folder named ...` 被通用规则误解析为目录名 `a`。发现后立即回退到 Shadow，没有继续扩大灰度。

修复内容：

- 增加英语 `create/mkdir + folder/directory + named/called + name` 专用规则。
- 通用新目录名规则禁止把 `a`、`an`、`one` 及其后的 `folder/directory` 当作名称。
- 增加完整英文名称保留与缺少名称时要求澄清的回归测试。

验证结果：

```text
AssistantConversationServiceTest  77/77
RAG 全量测试                       302/302
git diff --check                  通过
```

## 4. `admin-single` 发布

候选配置：

```text
deploy/generated/rag-execution/admin-single.candidate.20261020092552.env
```

发布前备份：

```text
deploy/generated/production-backups/20261020092605
```

备份包含 Cloud、Identity、RAG Execution 数据库和敏感配置快照，校验和与可读性检查全部通过。

发布脚本最终验收：

```text
[OK] local liveness reports the expected admin-single feature state
[OK] required runtime dependencies are healthy
[OK] public health is reachable and both internal route families are blocked
[OK] container is healthy with restart count 0
```

## 5. Android 真机执行闭环

确认前发送：

```text
Create a folder named rag-admin-single-check-20261020-1728
```

服务端在点击确认前已核对任务载荷。确认后的唯一业务结果：

```text
execution_id  9e28bc7a-e2ac-4ea3-93c0-68a1bfb3ba23
step_id       fb7bbebe-752e-4164-803e-329ee7938635
status        SUCCEEDED
attempts      1
result_code   FOLDER_CREATED
node_id       309
folder_name   rag-admin-single-check-20261020-1728
```

数据库交叉核对：

- RAG Execution 任务与步骤均为 `SUCCEEDED`。
- 事件顺序为登记、确认、开始、步骤成功、任务成功。
- CloudStorageApi 恰好有 `1` 条对应成功回执。
- `storage_node` 恰好有 `1` 个同名、未删除的根目录节点。
- 客户端显示“已在云端创建文件夹”。

## 6. 当前边界与后续门槛

- 保持 `admin-single + FOLDER_CREATE + RAG_ADMIN`，先完成生产稳定观察。
- Android 正式版云执行仍不得直接全量开放；稳定观察后再单独批准小比例客户端灰度。
- 批量和组合动作必须分别验收，不得借本次结果扩展 allowlist。
- 旧本地执行路径只能在兼容窗口结束且生产稳定记录完成后删除。
- 全面对话识别率优化继续作为后续独立阶段，不与当前灰度混改。

首次缺陷探针遗留了根目录节点 `308 / a`。该节点是用户可见业务数据，本次没有直接操作数据库删除；清理应由用户确认后通过正常云盘删除接口完成。

## 7. 后续稳定性与 Android 角色灰度准备

`admin-single` 发布后继续完成两轮无写入取消测试：

```text
Android Debug 真机     bbd6d948-629b-4996-ac8a-0936c9654f28  CANCELLED
Android Release 模拟器 b6a6dbe2-d1fd-4231-b961-4c762a8a4942  CANCELLED
```

两项任务都满足：完整目录名进入类型化载荷、版本从 `0` 变为 `1`、步骤 `attempts=0`、Cloud 回执为 `0`、同名节点为 `0`。前一阶段的 Shadow 任务 `07605f85-6227-4f5a-a6bf-e169505d88a7` 也已由过期维护转为 `EXPIRED`，没有产生回执或文件写入。

公共 APK 端点只维护单个 `CURRENT` 版本，没有按账号或百分比分发能力。为避免把管理员能力暴露给普通用户，Android 增加与服务端一致的双重门禁：

```text
BuildConfig.RAG_CLOUD_EXECUTION_ENABLED == true
Identity appRoles.rag == RAG_ADMIN
```

只有两个条件同时成立，客户端才进入云端确认路径；全局 `ADMIN`、`CLOUD_ADMIN` 或其他角色不能替代显式 `RAG_ADMIN`。Debug 与 Release 单元测试各 `191` 项全部通过。正式签名包先在隔离 Android 模拟器完成安装、登录、计划登记和云端取消验证；该验证阶段没有上传公共更新通道，真机 Debug 数据未被清除。

最终 Android 候选包信息：

```text
versionName  0.1.13
versionCode  14
file         alicia-cloud-android-0.1.13-14-release.apk
size         27634301 bytes
sha256       f20ec146bea1873c8d0dc03b7873bafcc3719a78477c71cd6f4fa2084e054463
signature    APK Signature Scheme v3，Alicia release certificate
```

该包保持旧本地写操作执行关闭，只开启受 `RAG_ADMIN` 角色约束的云端确认路径。公共通道发布及发布后下载哈希验证必须另行记录，不能仅凭本地构建成功判定上线完成。

## 8. Android 0.1.13 公共通道发布

角色门禁源码提交 `2b695cd` 同步到 Gitee 与 GitHub 后，通过 CloudStorageApi 管理接口上传最终签名包。Identity 临时管理员会话在上传完成后正常注销。

```text
public version       0.1.13
public uploadedAt    2026-10-20T09:53:31Z
download bytes       27634301
download sha256      f20ec146bea1873c8d0dc03b7873bafcc3719a78477c71cd6f4fa2084e054463
```

公开下载包由独立请求重新读取并计算哈希，结果与本地签名包清单完全一致。发布后的生产复核结果：

```text
stage                 admin-single
allowed action        FOLDER_CREATE
RAG execution health  healthy
API health            healthy
container restarts    0
recent execution log  no error/exception/panic/fatal/timeout
```

本次发布只完成管理员单人客户端灰度，不代表扩大服务端动作白名单，也不代表开始全面对话识别率优化。下一步应先观察真实管理员任务稳定性，再决定是否扩大账号范围。

## 9. 隔夜稳定性观察｜2026-10-21

`admin-single` 连续运行约 16 小时后完成第一次隔夜审计。生产配置仍严格保持 `RAG_ADMIN + FOLDER_CREATE`，公开健康检查、依赖检查、内部路由隔离和 Compose 配置检查全部通过。

```text
RAG Execution uptime   about 16 hours
Cloud API uptime       about 16 hours
RAG uptime             about 16 hours
container restarts     0
non-terminal tasks     0
failed/partial tasks   0
retried steps          0
event sequence gaps    0
receipt mismatches     0
```

数据库状态机审计后的任务总量：

```text
SUCCEEDED   2
CANCELLED   7
EXPIRED     5
```

两项成功任务都有且只有一个成功 Cloud 回执；所有取消和过期任务都没有 Cloud 回执。成功步骤不存在缺失回执，回执也不存在孤立、状态错误或 execution/step/action 对应错误。

RAG 服务在 2026-10-20 17:24（Asia/Shanghai）发布切换窗口记录过一次 Shadow 登记告警。该告警的 `sourceResponseId` 与任务 `07605f85-6227-4f5a-a6bf-e169505d88a7` 一致，9 秒后任务已成功持久化，最终按 TTL 转为 `EXPIRED`；没有丢失、重复执行或业务写入。Cloud API 日志中的 `next_retry_at` 只是常规后台 SQL 条件，不是执行重试。

当天使用专用 `RAG_ADMIN` 账号完成一轮无写入公网探针：

```text
execution_id  cf3f32e4-af2e-41fc-b5f5-32e679795e36
folder_name   rag-admin-stability-20261021-093220
status        CANCELLED
version       1
attempts      0
events        EXECUTION_REGISTERED -> EXECUTION_CANCELLED
receipts      0
storage nodes 0
```

探针完整验证了 Identity 登录、RAG 计划生成、精确英文目录名保留、任务登记、公网任务读取、取消、事件读取和临时会话注销。探针结束后 RAG、RAG Execution、Cloud API 均无新增异常日志。

本轮没有修改生产配置、服务镜像或动作白名单。继续保持 `admin-single` 观察，不进入全面意图识别优化，也不开放其他写动作。
