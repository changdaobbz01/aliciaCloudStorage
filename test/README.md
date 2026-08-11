# Alicia RAG Debug Web

本目录是 RAG 文件助手的本地调试页面和薄代理，不承载 RAG 业务逻辑。

当前目标：

- 输入自然语言文件需求。
- 展示 RAG 服务返回的最终意图识别模板。
- 自动携带 `conversationId`，验证缺槽位补全和确认短语不会丢失上一轮上下文。
- 支持通过“第一个”“选第 2 个”等输入验证上一轮真实候选快照选择。
- 用户确认后展示 `backendActionDraft`，验证 RAG 只生成后端请求草稿，不直接执行文件变更。
- 通过 `/api/assistant/contracts/action-bridge` 查看当前动作桥接契约。
- 使用本地 allowlist 适配器预检 `backendActionDraft`，只展示 CloudStorageApi 请求预览，不自动提交。
- 可在页面“测试配置”中保存当前用户 `Authorization`，用真实授权头验证候选绑定和集合预览。
- 验证 DeepSeek 解析、引导文案和本地兜底是否符合预期。
- 不展示 mock 文件树，不模拟真实文件操作。

结构：

- `public`：调试页面静态资源。
- `public/actionBridgeAdapter.js`：调用方消费 `backendActionDraft` 的前端参考适配器。
- `server.js`：本地静态服务，并把 `/api/*` 转发到 RAG 服务。
- RAG 业务配置和核心逻辑位于 `../rag`。

运行：

```powershell
npm start
```

默认会把接口转发到：

```text
http://127.0.0.1:8081
```

可通过环境变量覆盖：

```powershell
$env:RAG_API_BASE_URL="http://127.0.0.1:8081"
npm start
```

默认页面地址：

```text
http://127.0.0.1:8092
```

页面调试：

- `Authorization` 输入框保存的是完整请求头值，例如 `Bearer <token>`。
- 授权头只保存在当前浏览器会话的 `sessionStorage`。
- “新会话”会清空当前 `conversationId`，便于重新测试多轮流程。

后续演进：

- RAG 意图模板接入正式文件索引查询接口。
- 文件重命名、分享、删除、上传只输出动作草稿和执行桥接草稿，由 `CloudStorageApi` 做权限校验和执行。
- 删除、分享、重命名继续保留显式用户确认。
