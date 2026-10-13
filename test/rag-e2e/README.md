# RAG 本地隔离评测

这套工具用于对“识别 → 登记 → 用户确认 → 云端执行”链路做可重复的基线与回归评测。它不会读取或覆盖仓库根目录的 `.env`，不会连接生产 CloudStorageApi / RAG Execution 数据库，也不会修改生产 Compose。

## 安全边界

- Compose 项目固定为 `alicia-rag-e2e`，数据库使用独立 Docker 卷。
- DB、CloudStorageApi、RAG 和 RAG Execution 仅绑定 `127.0.0.1` 的 `18xxx` 端口。
- RAG Execution 唯一允许的执行动作是 `FOLDER_CREATE`。
- 普通评测只识别并登记任务，不确认、不执行；评测结束前会取消本轮产生的所有未确认任务。
- 文件夹执行需要命令行开关和 `.env.local` 开关同时开启。
- COS 凭据为空，上传和对象清理任务关闭；评测不能接触真实对象存储。
- `.env.local`、`build/` 与 `results/` 已被 Git 忽略。令牌仅保存在评测进程内存中，报告不写入令牌或密码。

Identity 可以使用正式统一登录入口，但必须提供专用测试账号。该账号只授予 `appRoles.rag=RAG_ADMIN`；不要授予全局 `ADMIN` 或 `appRoles.cloud=CLOUD_ADMIN`。登录会在 Identity 创建正常会话，但所有云盘写入仅发生在本地隔离数据库。

## 首次准备

在仓库根目录执行：

```powershell
pwsh -File test/rag-e2e/prepare-local-env.ps1
```

然后只在本机编辑 `test/rag-e2e/.env.local`，填写：

```text
ALICIA_RAG_E2E_ACCOUNT
ALICIA_RAG_E2E_PASSWORD
DEEPSEEK_API_KEY
```

不要把密码或 API Key 发到聊天中。脚本生成的数据库密码与两个服务签名密钥互不复用，也不会打印到终端。

## 启动与识别基线

```powershell
pwsh -File test/rag-e2e/verify-static.ps1
pwsh -File test/rag-e2e/start-local.ps1
pwsh -File test/rag-e2e/run-evaluation.ps1
```

`start-local.ps1` 先使用仓库 Maven Wrapper 在宿主机生成经过相同源码验证的 RAG JAR，再把该产物封装进仅供本地评测的运行时镜像。这样不改变生产 Dockerfile，也避免 Docker 构建器重复下载 Maven 发行包。

当前套件覆盖对话与产品问答、能力边界、提示词注入、搜索语义、原子文件操作、上传、文件夹创建、多轮槽位补全、组合操作和集合批处理。断言不仅检查 `intentId`，还会按场景检查实体、缺失槽位、语义操作、查询模式、作用域、结果类型、确认要求和任务登记状态。每次响应、断言、执行结果与任务清理结果都会写入 Git 忽略的 `results/rag-e2e-<runId>.json`，便于逐项修复和回归对比。

## 显式执行唯一安全动作

先把 `.env.local` 中的：

```text
ALICIA_RAG_E2E_ALLOW_LOCAL_EXECUTION=true
```

再执行：

```powershell
pwsh -File test/rag-e2e/run-evaluation.ps1 -ExecuteFolderCreate
```

评测器只确认标记为 `safeExecution` 的文件夹创建用例，轮询到终态后还会从本地 CloudStorageApi 查询并核对生成的唯一文件夹。其他任务即使登记成功也不会被确认。

执行验证完成后，把 `ALICIA_RAG_E2E_ALLOW_LOCAL_EXECUTION` 恢复为 `false`，避免后续误触发本地写入。

## 停止与清理

保留本地测试数据：

```powershell
pwsh -File test/rag-e2e/stop-local.ps1
```

删除这个固定 Compose 项目的测试数据库卷：

```powershell
pwsh -File test/rag-e2e/stop-local.ps1 -PurgeData
```

`-PurgeData` 只删除 `alicia-rag-e2e` 项目资源，不处理仓库根 `.env`、生产容器或其他 Docker 卷。
