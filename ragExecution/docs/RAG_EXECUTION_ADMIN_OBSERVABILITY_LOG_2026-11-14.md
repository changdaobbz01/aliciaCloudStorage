# RAG Execution 管理员只读可观测面完成日志

日期：2026-11-14

## 1. 结论

RAG Execution 管理员只读可观测面已完成本地实现与回归，尚未部署生产。该能力用于查看任务总体状态、
按状态或 owner 查询任务，以及检查单个任务的步骤和公开事件顺序，不提供确认、取消、重试、删除或任何
业务写入入口。

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

本日志记录的是本地完成状态，不代表生产已上线。后续若批准发布，应成对更新 `rag-execution` 与
`frontend`，发布前后分别验证：

- 生产 stage、角色和动作白名单没有变化；
- 三个管理端点对 `RAG_ADMIN` 可读、对 `RAG_USER` 返回 403；
- 管理台没有写操作控件；
- 既有任务、回执、事件序列和 Cloud/RAG 依赖健康；
- 容器重启、异常日志和公开 Android 版本没有非预期变化。
