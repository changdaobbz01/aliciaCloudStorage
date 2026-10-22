# RAG 本地隔离评测

这套工具用于对“识别 → 登记 → 用户确认 → 云端执行”链路做可重复的基线与回归评测。它不会读取或覆盖仓库根目录的 `.env`，不会连接生产 CloudStorageApi / RAG Execution 数据库，也不会修改生产 Compose。

## 安全边界

- Compose 项目固定为 `alicia-rag-e2e`，数据库使用独立 Docker 卷。
- DB、CloudStorageApi、RAG 和 RAG Execution 仅绑定 `127.0.0.1` 的 `18xxx` 端口。
- RAG Execution 唯一允许的执行动作是 `FOLDER_CREATE`。
- 普通评测只识别并登记任务，不确认、不执行；评测结束前会取消本轮产生的所有未确认任务。
- 对抗场景会验证否定、假设、引用命令、互斥动作、无上下文代词和自然语言撤销都不会生成可执行计划。
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

基线通过后可运行独立的高压套件，或一次串行运行两套场景：

```powershell
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite stress
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite all
```

高压套件重点覆盖口语噪声、否定与条件句、引用命令、冲突动作、无上下文确认、多轮撤销和上下文切换。它沿用相同的隔离与清理规则，不会确认或执行任务。

完整套件还会核对 `contract-baseline.json`。基线只锁定可执行语义契约，包括意图、实体、缺失槽位、语义操作、作用域、动作草稿、安全状态和任务登记状态；模型措辞、置信度、UUID 与时间戳不参与比较。这样即使两次运行都显示 87/87，只要核心结果发生漂移，评测仍会失败。报告中的 `recognitionSources` 会单独统计 DeepSeek 与本地兜底响应数量，用来区分语义回归和上游模型降级。

## 阶段 8 识别准确率评测

`recognition-dev.json` 和 `recognition-holdout.json` 与 87 项契约套件职责不同：开发集用于错误分类和优化，保留集只在一个开发批次完成后检查泛化。`recognition-candidate.json` 只保存尚未并入正式开发集的新泛化样本，允许在不改变历史双基线、不提前运行保留集的前提下先测量和修复。候选样本稳定后才一次性并入正式开发集；晋升后候选队列允许为空，此时直接运行 `recognition-candidate` 会明确失败，而不会生成无意义的零样本报告。运行后会生成原始报告和 `rag-recognition-<split>-<runId>.metrics.json`，统计意图准确率、语义操作准确率、完整契约准确率、分组结果、混淆矩阵和识别来源。

```powershell
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite recognition-dev

# 只评测尚未并入正式开发集的新泛化样本
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite recognition-candidate

# 只在一个开发节点完成后运行
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite recognition-holdout
```

准确率不达标属于阶段 8 的测量结果，不会让命令伪装成基础设施失败；但缺失响应、API/清理失败或任何期望非执行话语产生可执行计划，仍会直接失败。两套识别评测都禁止使用 `-ExecuteFolderCreate`，不会确认任务。

建立 DeepSeek 在线基线时必须显式启用在线 canary。脚本会先提交一条无执行能力的社交问候，要求存在可审计的模型成功证据、正确意图且不存在动作草稿或任务引用。模型结果可以直接保留为 `deepseek`，也可以在成功返回后由本地权威规则安全接管为 `local_policy`；HTTP、超时、无效 JSON、模型不可用或无来源证据的本地兜底都会在数据集开始前终止，并只输出脱敏诊断：

```powershell
pwsh -File test/rag-e2e/run-evaluation.ps1 `
  -Suite recognition-dev `
  -RequireOnlineRecognition
```

canary 通过只证明在线识别链路可用。正式指标会进一步记录 `recognitionPaths`：模型结果直接保留为 `deepseek`，模型前安全拦截为 `local_guard`，无需调用模型的确定性结构语义为 `local_structure`，在线模型成功后由本地权威规则接管为 `local_policy`，模型不可用或响应不可解析才是 `local_unavailable`。`write-recognition-baseline.ps1 -Lane deepseek-online` 允许前四条可审计路径，并要求存在真实在线成功证据；任何 `local_unavailable` 都会拒绝写入基线。

在线稳定性必须使用同一干净提交、同一开发集的至少三份指标报告。聚合器统计逐样本的意图、操作、
识别路径和完整契约结果漂移；它不会把准确率波动伪装成基础设施失败，但发现不安全执行、缺失响应、
基础设施错误、`local_unavailable`、`unclassified` 或缺少在线模型证据时会失败关闭：

```powershell
$metrics = @(
  'test/rag-e2e/results/rag-recognition-development-<run-1>.metrics.json',
  'test/rag-e2e/results/rag-recognition-development-<run-2>.metrics.json',
  'test/rag-e2e/results/rag-recognition-development-<run-3>.metrics.json'
)
& test/rag-e2e/measure-recognition-stability.ps1 -MetricsPath $metrics
```

稳定性测量只使用开发集。不要为了增加样本量反复运行保留集；保留集仍只在完成一个独立开发节点后
运行一次。

阶段 8F 使用 `recognition-error-taxonomy.json` 统一区分可用性故障、安全违规、安全语义、参数契约、
操作语义、集合语义、会话边界、普通意图路由、采样结果波动和仅仲裁路径波动。分类器不会读取或输出
用户原句，只保存数据集样本键和结构化期望/实际结果：

```powershell
pwsh -File test/rag-e2e/classify-recognition-errors.ps1 `
  -MetricsPath test/rag-e2e/results/rag-recognition-holdout-<runId>.metrics.json `
  -StabilityPath test/rag-e2e/recognition-stability.deepseek-online.json
```

分类结果只用于确定下一批通用优化方向，不能作为复制保留集原句进规则或开发集的依据。
候选套件报告使用 `development-candidate` split，分类器会自动核对
`recognition-candidate.json`；也可以通过 `-DatasetPath` 显式指定受审查的数据集。

已审查的在线错误分类基线保存在 `recognition-error-baseline.deepseek-online.json`。静态门禁会核对
分类法哈希、开发集与保留集哈希、来源提交、统计一致性和阻断级类别；数据集或分类法变化后必须
显式重建，不能让历史分类继续伪装成当前结果。该基线只保存样本键和结构化字段，不保存用户原句。

本地兜底基线必须由同一个干净提交生成的开发集与保留集指标共同写入，且两份报告都只能包含 `local_fallback`：

```powershell
pwsh -File test/rag-e2e/write-recognition-baseline.ps1 `
  -DevelopmentMetricsPath test/rag-e2e/results/rag-recognition-development-<runId>.metrics.json `
  -HoldoutMetricsPath test/rag-e2e/results/rag-recognition-holdout-<runId>.metrics.json
```

脚本拒绝覆盖已有基线、脏源码、来源路径不可解释、缺失响应、基础设施失败或不安全执行结果。DeepSeek 在线基线必须单独使用 `-Lane deepseek-online` 生成；其中可以包含设计上必需的 `local_guard` 和在线成功后的 `local_policy`，但不得包含 `local_unavailable`，不能把模型降级报告冒充在线基线。

当前本地兜底基线保存在 `recognition-baseline.local-fallback.json`。它是准确率比较起点，不是必须永远保持输出不变的契约快照；执行语义不可退化仍由 `contract-baseline.json` 单独负责。

只有至少三次连续的 `-Suite all` 识别评测全部通过且契约完全一致时，才能首次写入基线：

```powershell
$reports = @(
  'test/rag-e2e/results/rag-e2e-<run-1>.json',
  'test/rag-e2e/results/rag-e2e-<run-2>.json',
  'test/rag-e2e/results/rag-e2e-<run-3>.json'
)
& test/rag-e2e/verify-result-baseline.ps1 -WriteBaseline -ReportPath $reports
```

已有基线不得由脚本自动覆盖。场景或契约确需变更时，应先审查差异、删除旧基线、重新完成三次一致性评测，再显式生成新基线。也可以手动复核任意报告：

```powershell
& test/rag-e2e/verify-result-baseline.ps1 `
  -ReportPath test/rag-e2e/results/rag-e2e-<run-id>.json
```

`start-local.ps1` 先使用仓库 Maven Wrapper 在宿主机生成经过相同源码验证的 RAG JAR，再把该产物封装进仅供本地评测的运行时镜像。这样不改变生产 Dockerfile，也避免 Docker 构建器重复下载 Maven 发行包。

当前套件覆盖自然对话与产品问答、能力边界、提示词注入、自然搜索、歧义与非执行话语保护、原子文件操作、上传、文件夹创建、多轮槽位补全、搜索纠正与操作撤销、组合操作，以及自然语言集合批处理。断言不仅检查 `intentId`，还会按场景检查实体、缺失槽位、语义操作、查询模式、作用域、结果类型、确认要求和任务登记状态。每次响应、断言、执行结果与任务清理结果都会写入 Git 忽略的 `results/rag-e2e-<runId>.json`，便于逐项修复和回归对比。

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
