# Action Bridge Client Adapter（已退役）

客户端 Action Bridge 适配器已于 2026-10-21 随 RAG 执行分离阶段 7 退役。移动端和普通 Web 客户端不得把 `backendActionDraft` 转换成 CloudStorageApi 写请求。

当前唯一写执行链路：

```text
RAG typed ActionPlan
  -> ragExecution immutable task
  -> user confirmation by executionId + expectedVersion
  -> fixed internal CloudStorageApi action endpoint
```

公网 RAG 响应只保留 `backendActionDraft` 的只读兼容元数据；`method`、`pathTemplate`、`path`、`contentType`、`pathVariables`、`queryParameters` 和 `body` 均被移除，`executableByBackend=false`。旧客户端因此只能拒绝执行，不能恢复本地写路径。

客户端仍可执行的本地职责只有：

- 展示计划、候选、风险和任务状态。
- 使用 `executionReference` 确认、取消和查询云端任务。
- 在任务进入 `WAITING_CLIENT_INPUT` 后选择并上传本地文件字节。
- 页面导航、文件预览、下载和系统分享。

`test/public/actionBridgeAdapter.js` 只作为历史契约解析测试保留，不属于任何生产运行路径，也不得重新接回客户端。
