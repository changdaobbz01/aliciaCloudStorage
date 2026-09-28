# RAG 执行动作契约基线

## 文档用途

- 冻结日期：2026-09-18
- 对应阶段：RAG 执行分离阶段 0
- 识别契约版本：`action_plan_v2`
- 当前 Android 本地执行器：保留但 `executionEnabled=false`

本文件只记录迁移前已经存在的动作形态与新协议映射。架构、安全、状态机和实施顺序以 [RAG 执行服务分离实施方案](RAG_EXECUTION_SEPARATION_PLAN.md) 为唯一事实来源。

## 当前动作映射

| RAG 当前动作 | 当前 CloudStorageApi 业务能力 | 新执行动作 | 分离批次 |
| --- | --- | --- | --- |
| `rename` | 单节点重命名 | `NODE_RENAME` | 第一批 |
| `delete` | 单节点移入回收站 | `NODE_TRASH` | 第一批 |
| `folder.create` | 新建目录 | `FOLDER_CREATE` | 第一批 |
| `share` | 创建分享 | `SHARE_CREATE` | 第一批 |
| 单节点移动语义 | 单节点移动 | `NODE_MOVE` | 第一批 |
| `collection.trash*` | 批量或受控范围移入回收站 | `NODE_BATCH_TRASH` | 第二批 |
| `collection.move*` | 批量移动 | `NODE_BATCH_MOVE` | 第二批 |
| `collection.rename_add_prefix` | 批量重命名 | `NODE_BATCH_RENAME` | 第二批 |
| `file.upload` / `upload_target` | 客户端上传本地字节 | `UPLOAD_FILES` | 第三批，客户端参与 |
| `composite.*` | 多步骤组合 | 原子步骤集合 | 第三批 |

## 第一批类型化字段

### `NODE_RENAME`

```text
nodeId: positive long
expectedNodeVersion: optional non-negative long; newly persisted JPA entities start at 0
newName: non-blank string, max 255
```

### `NODE_TRASH`

```text
nodeId: positive long
expectedNodeVersion: optional non-negative long; newly persisted JPA entities start at 0
```

### `NODE_MOVE`

```text
nodeId: positive long
expectedNodeVersion: optional non-negative long; newly persisted JPA entities start at 0
destinationParentId: optional positive long; null means root
```

### `FOLDER_CREATE`

```text
parentId: optional positive long; null means root
folderName: non-blank string, max 255
```

### `SHARE_CREATE`

```text
nodeIds: 1-20 unique positive longs
title: optional string, max 255
password: 当前禁止进入持久化执行载荷；云端任务池仅接受无密码分享
expiresInDays: optional integer, 1-365 when present
allowDownload: boolean
allowSave: boolean
```

分享密码属于秘密输入。在引入专用的短时秘密输入或信封加密协议前，不得把密码发送给
`ragExecution`，也不得写入 `rag_execution_step.payload_json`、事件或日志。默认关闭或
shadow 阶段仍由既有客户端路径处理；云端执行模式开启后，缺少任务引用时必须失败关闭，
不得为了兼容而回退为本地执行。

CloudStorageApi 在真正执行时仍必须重新执行资源归属、名称、目录、分享限制和业务状态校验；以上字段校验不能替代领域校验。

## 明确冻结项

阶段 0 与阶段 1 不修改：

- RAG 意图、Prompt、SemanticFrame、候选绑定和回复文案。
- `ActionPlan` 与 `BackendActionDraft` 的当前客户端输出。
- Android 本地执行器的默认关闭状态。
- CloudStorageApi 现有公开文件、目录和分享接口。
- Web 与 Android 的当前确认交互。

阶段 1 的新服务不允许出现：

- CloudStorageApi Java 模块编译依赖。
- `/api/storage/**`、`/api/share-links/**` 或内部写动作客户端。
- 任意 `method/path/body` 执行协议。
- 保存用户 Access Token 或 Refresh Token 的字段。

## 基线验证

平台完成日志记录的迁移前基线：

```text
RAG：277 tests，0 failures，0 errors
CloudStorageApi：251 tests，0 failures，0 errors
```

新模块的 `LegacyExecutionBaselineTest` 固定 `action_plan_v2` 和 Android 执行开关默认关闭；`ExecutionIsolationBoundaryTest` 固定阶段 1 不含云盘写调用。后续阶段改变这些边界时，必须先更新实施方案和对应测试，不能静默放宽。
