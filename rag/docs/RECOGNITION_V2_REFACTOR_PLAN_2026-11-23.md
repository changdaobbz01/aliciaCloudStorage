# RAG Recognition V2 对话识别改造计划

日期：2026-11-23
状态：T0 基线冻结已完成，T1 和 T2 已通过本地验收
范围：只改造 `rag` 的会话理解与识别链路；`ragExecution`、CloudStorageApi 和生产执行权限保持不变。

## 1. 目标与边界

本计划解决当前“开发集指标很高，但真实使用仍感觉识别不足”的问题。改造目标不是继续追加原句正则，而是建立：

- 可追踪的识别决策链；
- 可跨重启和 token 刷新的持久会话；
- Embedding、关键词和字面检索组合的混合召回；
- 单一、可解释的 SemanticFrame 决策权；
- 授权、脱敏、可复现的真实失败样本闭环；
- 新旧识别器的无执行 Shadow 对比与可回退灰度。

整个改造期间生产执行边界固定为：

```text
stage          admin-single
role           RAG_ADMIN
allowed action FOLDER_CREATE
```

禁止把识别准确率提升解释为执行授权。禁止让模型直接调用 CloudStorageApi，也不把自然语言理解迁入
`ragExecution`。`ragExecution` 继续只接收版本化 ActionPlan，负责持久任务、确认、状态机、重试、过期、
事件和 Cloud 动作分发。

## 2. 当前架构事实

当前 Android 只向 RAG 发送当前消息、`conversationId`、当前目录、附件数量和客户端事件；完整界面对话历史
不会重新提交。RAG 服务端会话仅保存在进程内存中，默认 TTL 30 分钟、最多 500 个会话，并使用完整
Authorization 的 SHA-256 指纹隔离会话。容器重启会丢失状态，access token 更新也可能使旧会话无法恢复。

当前模型与编排事实：

```text
intent model       deepseek-v4-flash / temperature 0.1 / max_tokens 900 / timeout 8s
context model      deepseek-v4-flash / temperature 0.05 / max_tokens 1200 / timeout 20s
reply polisher     deepseek-v4-flash / temperature 0.72 / max_tokens 600 / timeout 20s
retrieval          中文双字 gram + Jaccard/containment/substring，最多返回 5 条
semantic examples  338
semantic boundaries 449
intent rules       49
entity rules       38
```

当前普通识别结果可能依次被模型输出、本地 IntentRouter、SemanticRecognitionArbiter、SemanticFrameResolver、
CapabilityBoundary、NonExecutable Guard、Constraint Guard 和 ConversationService 改写。V2 的目标是让模型
负责理解、Validator 负责校验、安全门禁负责阻断，避免多个组件竞争最终意图解释权。

## 3. T0 冻结基线

### 3.1 代码与生产快照

```text
local Cloud commit             d549b36
Gitee / GitHub                 d549b36 / d549b36
production Cloud commit        a169030
local mainSite commit          9c43cfa
production mainSite commit     eb79b3d
production tracked state       clean / clean
production disk                25% used / 43G available
RAG / Execution restarts       0 / 0
public health                  all checked paths HTTP 200
recent 24h anomaly matches     0 / 0
```

生产 `a169030` 与本地 `d549b36` 的差异是后者新增管理员可观测面完成日志；生产运行代码没有因此落后。

### 3.2 Development 基线

```text
dataset                         rag_recognition_dev_v16
cases / turns                   402 / 516
local intent / exact            516 / 516
online intent / exact           516 / 516
semantic operation              284 / 284
unsafe executable               0
online stability runs           3
intent / operation drift        0 / 0
exact outcome drift             0
path drift                      3
fully stable                    513 / 516
```

### 3.3 封存 Holdout 基线

Holdout 不因 T0 重跑，也不读取原句进行规则调优。当前携带的既有证据是：

```text
dataset                         rag_recognition_holdout_v1
cases / turns                   24 / 29
local intent                    23 / 29
local operation                 12 / 17
local exact                     22 / 29
online intent                   28 / 29
online operation                16 / 17
online exact                    28 / 29
unsafe executable               0
```

这组差距证明 development 516/516 只代表已知分布回归，不代表真实未知表达已经解决。后续优化不得只用
development 满分作为上线依据。

冻结哈希：

```text
development  b3f25ec1a40b191ae419b85cd4e3b73a616c5a98afe6ee41c27f660a2e9ac103
holdout      eebeb9d595799ee049fd5d349d36801b13f59fadd1f1f70a1e8e9aef8639abb7
```

只读验证命令：

```powershell
pwsh -NoProfile -File test/rag-e2e/verify-recognition-v2-baseline.ps1
```

## 4. 统一错误分类

Recognition V2 使用下面的互斥主类别；一次请求可以附带多个次级标签，但必须有且仅有一个主类别：

| 主类别 | 定义 | 是否属于识别错误 |
| --- | --- | --- |
| `availability_failure` | 模型、Storage、Identity、Redis 或网络不可用 | 否，基础设施错误 |
| `safety_violation` | 不应执行的表达产生了可确认或可执行计划 | 是，阻断级 |
| `intent_error` | 主意图错误 | 是 |
| `operation_error` | 意图大类正确但操作语义错误 | 是 |
| `entity_error` | 对象、名称、目标或参数提取错误 | 是 |
| `scope_error` | 目录、集合、数量、时间或大小范围错误 | 是 |
| `context_error` | 指代、补槽、修正、取消或话题切换承接错误 | 是 |
| `retrieval_error` | 召回例句缺失、无关或边界/正例混淆 | 是 |
| `rule_override_error` | 模型正确但本地规则、仲裁或 Guard 改错 | 是 |
| `candidate_binding_error` | 语义正确但真实文件候选绑定错误 | 否，绑定错误 |
| `capability_rejection` | 当前契约不支持该范围或动作 | 否，能力边界 |
| `execution_policy_rejection` | 角色或生产 allowlist 不允许执行 | 否，执行策略 |
| `response_quality` | 意图正确但回复生硬、误导或不清晰 | 否，表达质量 |

质量报表必须分别统计识别错误、依赖错误、候选绑定错误和执行策略拒绝，不能再把它们合并为“识别失败”。

## 5. Trace 隐私契约

T1 的识别 trace 默认不得保存：

- Authorization、access token、refresh token、Cookie、API key；
- 原始用户 ID、手机号、邮箱、设备标识、IP；
- nodeId、fileId、folderId、ownerId；
- COS object key、存储路径、上传路径和下载 URL；
- 原始文件名、目录名、模型完整 Prompt 或完整响应正文；
- BackendActionDraft body、执行 payload/result 和内部错误堆栈。

默认 trace 只保存结构、枚举、布尔值、数量、耗时、来源、规则 ID、模型版本、Prompt 版本和经过脱敏的
SemanticFrame。需要关联同一主体时使用服务端密钥生成的不可逆伪匿名标识，不能使用可枚举的普通 SHA-256。

只有用户明确授权后，某条真实表达才可以复制到独立样本候选区；复制前必须删除或替换身份、路径、文件名、
目录名、URL 和业务内容。授权样本与运行 trace 分库存储，不携带 userId 或 conversationId，不能自动晋升到
development 或 holdout。

## 6. 质量门禁

每个后续节点必须同时满足：

1. 冻结基线校验通过，数据集和 holdout 哈希没有变化；
2. `unsafeExecutableCount=0`；
3. 识别评测不确认、不执行真实任务；
4. development 不回归；
5. 新增候选先冻结标签再读取新实现结果；
6. holdout 不因开发集变化而反复运行；
7. 生产 Shadow 不改变用户可见结果，不登记新执行任务；
8. 任何生产切换都有显式开关、备份、回滚和独立验收；
9. 不扩大 RAG 角色、动作 allowlist、批量或组合执行权限。

## 7. 有序任务清单

| ID | 状态 | 任务 | 完成门槛 |
| --- | --- | --- | --- |
| T0 | 已完成 | 冻结现状、指标、隐私和错误分类 | 基线脚本、文档和只读验证全部通过 |
| T1 | 本地验收通过 | 识别链路可观测性 | 可区分会话、模型、仲裁、门禁、绑定、计划和登记结果，隐私测试通过 |
| T2 | 本地验收通过 | 授权反馈与失败样本闭环 | 分类只含追踪 ID 和类别，样本需预览及授权 |
| T3 | 本地验收通过 | Redis 持久会话 | 重启、token 更新和多实例不丢上下文 |
| T4 | 未开始 | 混合语义检索 | 新盲测召回优于旧检索，unsafe 仍为 0 |
| T5 | 未开始 | 统一 AI 编排器 | 唯一 SemanticFrame 决策链，Guard 只验证或阻断 |
| T6 | 未开始 | 独立盲测和故障评测 | 未见表达改善，安全与降级门禁通过 |
| T7 | 未开始 | Legacy/V2 生产 Shadow | 新结果持续优于旧结果且没有用户或业务副作用 |
| T8 | 未开始 | 灰度切换、回滚与清理 | V2 成为主路径，旧分支安全退出 |

## 8. T0 完成条件

- [x] 两个本地仓库和用户原有工作树状态已确认；
- [x] SSH 直连和生产服务器只读访问已确认；
- [x] 生产 stage、角色、动作、健康、重启、日志和磁盘已确认；
- [x] development、holdout、candidate、稳定性和语料库存已盘点；
- [x] 错误分类、Trace 隐私契约和全阶段门禁已定义；
- [x] 新增基线验证脚本执行通过；
- [x] `git diff --check` 和精确范围审查通过；
- [x] T0 文档与脚本单独提交并同步双远端。

## 9. T1 识别链路追踪

追踪使用独立随机 UUID。开启后，鉴权通过的计划请求通过 HTTP 响应头 `X-Recognition-Trace-Id`
提供该 UUID。普通 JSON、兼容识别接口和 SSE 共享相同的追踪格式。SSE 计划线程和登记线程显式关联
同一请求，发生超时或发送错误时保留已经完成的阶段。

| 阶段 | 诊断证据 |
| --- | --- |
| 会话 | 新建、恢复、重置或结束，附轮数、待补槽位与候选数量 |
| 检索与模型 | 静态例句 ID、分数、模型与提示版本、HTTP 状态、是否降级、模型语义提案 |
| 路由、仲裁与校验 | 本地意图、仲裁来源、各门禁是否命中、规范化后的语义结构 |
| 编排 | 候选选择、上下文改写、补槽、对象绑定和 ActionPlan 状态 |
| 登记 | 成功、跳过、动作白名单拒绝、访问被拒、其他拒绝或登记结果不确定 |

追踪使用白名单字段投影，语义结构只保留枚举、计数和布尔值。例如，对象只保留数量，追问只保留是否需要
澄清，登记拒绝只保留分类。最近 256 条完成的追踪保存在 RAG JVM 内存中，每条最多 48 个阶段记录。
旧记录随容量淘汰，进程退出时清空。本地开发可在进程内按追踪 UUID 查询。

配置项为 `alicia.rag.recognition.trace.enabled`，对应环境变量 `ALICIA_RAG_RECOGNITION_TRACE_ENABLED`，
默认值为 `false`。此节点在本地隔离环境显式开启，
生产启用及管理员查询方式留待单独验收。

本地回归使用已有工具和标签。开发集 516/516、语义操作 284/284，unsafe 和 executed 均为 0。
基线加高压契约 87/87，与既有契约哈希一致。两轮使用离线识别，评测记录源为 T1 待提交工作树，
原冻结数据集和线上基线继续作为独立证据。

本地 HTTP 验收覆盖 `/api/assistant/plan`、`/api/intent/recognize` 和 `/api/assistant/plan/stream`。
三个接口均返回 200 和各自的追踪 ID，SSE 正常发送最终事件。临时 Identity 评测会话已注销。
RAG 的 396 项测试和 RAG Execution 的 59 项测试全部通过。

## 10. T2 授权反馈与候选样本

Android 在已完成的助手回复下提供「识别反馈」。该入口依赖服务返回有效的 `X-Recognition-Trace-Id`
和 `X-Recognition-Feedback`。后者为 `category` 时只提供分类反馈，为 `category,candidate` 时可补充样本。

| 接口 | 请求字段 | 处理结果 |
| --- | --- | --- |
| `POST /api/assistant/feedback` | `traceId`, `category` | 记录评价类别 |
| `POST /api/assistant/feedback/candidate/preview` | `traceId`, `expression` | 返回脱敏预览，内存保存摘要 |
| `POST /api/assistant/feedback/candidate` | `traceId`, `expression`, `consent` | 保存经预览和确认的表达，返回独立样本编号 |

三个入口沿用 RAG 鉴权。追踪归属用仅在 JVM 内有效的 HMAC 标签校验，同一账号续签后可以继续反馈。
类别反馈在进程内保留最多 256 条。同一追踪的一次分类提交和一次样本提交均支持幂等重试。

样本填写框初始为空。用户以 `【文件】`、`【目录】`、`【人员】` 等占位符替换真实名称和业务内容，
随后主动发送草稿生成预览。服务端遮盖可识别的凭据、链接、邮箱、手机号、路径、编号和文件名等，
再由用户检查剩余文本并单独勾选授权。未加标记的人名、目录名称和业务含义仍需用户自行判断和改写。
服务端只保存与最近预览相同且经明确授权的表达。

候选表达独立保存为 `candidate-<样本编号>.json`，包含编号、类别、脱敏表达、授权版本、创建时间，
数据集标记固定为 `candidate`。样本文件与会话和追踪标识隔离。容量为 512 条，满额后由维护人员
评审和整理。用户可提供回执编号申请撤回，由维护人员在专用目录按精确编号删除文件。

启用设置如下。分类反馈和追踪均默认关闭，候选目录留空时只启用分类反馈。目录应位于 Git 工作树之外，
由 RAG 服务账号独占。Linux 目录使用权限 `700`，样本文件使用权限 `600`。Windows 使用仅服务账号可访问的 ACL。

```text
ALICIA_RAG_RECOGNITION_TRACE_ENABLED=true
ALICIA_RAG_RECOGNITION_FEEDBACK_ENABLED=true
ALICIA_RAG_RECOGNITION_FEEDBACK_CANDIDATE_DIRECTORY=<专用候选目录>
```

评审时先核对脱敏和表达的可复现性，再为拟纳入的样本补充合成上下文及独立标注。
评审通过的案例可手动录入既有 `recognition-candidate.json` 并执行候选评测。
开发集晋升与盲测集冻结沿用现有流程，各自单独审查。

本地验收覆盖同账号续签、跨账号拒绝、重复提交、严格字段、预览与落盘分离、授权校验和样本文件字段。
通过真实 HTTP 请求完成 SSE 回复、分类反馈、脱敏预览和授权提交，临时 Identity 会话已注销。
RAG 与 RAG Execution 共 458 项测试通过，Android 160 项单测和 Debug 构建通过。
冻结基线和执行契约的静态校验通过。测试使用合成样本，生产启用待独立发布验收。

## 11. T3 会话持久化

| 模式 | 读取来源 | 写入与验证 |
| --- | --- | --- |
| `MEMORY` | 本机内存 | 延续现有默认路径 |
| `SHADOW` | 本机内存 | 镜像写 Redis，后续读取时比对两份会话并记录脱敏追踪结果 |
| `REDIS` | Redis | 以会话轮次作乐观版本，原子写入新状态并刷新 TTL |

Redis 的路由键由 Identity 已鉴权的账号标识和随机会话 ID 经 HMAC 生成。会话与 access token
轮换解耦。存储值使用 AES-256-GCM 加密并带格式版本，密文绑定路由键。Redis 键和值里均无明文
账号标识和 access token。会话状态保留意图、槽位、候选、任务焦点和待确认计划，并保存最多 8 轮
用户输入及可见回复，单段最多 512 个字符。模型继续使用结构化上下文，近期原始对话暂存于加密会话用于后续编排。

失联或存储格式无法读取时，从空白会话处理本次请求。若已恢复旧上下文但随后无法提交新版本，接口返回
可重试错误，计划在登记前停止。并发冲突返回 409，SSE 给出重发提示。Redis 中的重置和执行结束事件
会移除旧会话版本，使尚在处理的旧请求无法复活它。集合操作仍依赖现有候选快照校验，授权与任务状态继续
由 Identity 和 RAG Execution 分别管理。

```text
ALICIA_RAG_CONVERSATION_PERSISTENCE_MODE=MEMORY
ALICIA_RAG_CONVERSATION_REDIS_URL=<专用 Redis 的私网连接地址>
ALICIA_RAG_CONVERSATION_REDIS_KEY=<随机 32 字节的 Base64，保存在服务秘密配置>
ALICIA_RAG_CONVERSATION_REDIS_TIMEOUT_MS=600
ALICIA_RAG_CONVERSATION_TTL_MINUTES=30
```

Redis 模式有效 TTL 为 1 至 120 分钟，每次成功保存后续期。需要跨 Redis 重启保留会话时，应在 Redis
侧使用持久化卷和 AOF。多个 RAG 实例使用相同的私有 Redis 和会话密钥。密钥更换会开启新一批会话，
旧记录随后按 TTL 到期。生产安排为先 `SHADOW`、检查存储匹配和中断记录，再切换 `REDIS`。
发生影子写异常时，切换前应处理可能残留的会话。可回退到 `MEMORY`，回退后会话从新上下文开始。

真实 Redis 集成测试仅接受本机回环地址。启动专用、可丢弃的测试 Redis 后运行如下命令。

```powershell
$env:ALICIA_TEST_RAG_REDIS_URL = 'redis://127.0.0.1:16379'
.\mvnw.cmd -o -pl rag,ragExecution -am test
```

本地验收使用 Redis 7.4。RAG 406 项测试、RAG Execution 59 项测试全部通过，覆盖跨实例接续、
账号隔离、过期、影子比对及切换、断连降级、并发覆盖和旧会话失效。真实 HTTP 请求在 RAG 容器重启
及 Identity 令牌轮换后沿用原会话 ID，轮次从 1 递增到 2，并正确修正搜索词。临时 Identity 会话已注销。

Redis 主读模式下，离线开发集意图及精确契约均为 516/516，语义操作为 284/284，危险可执行输出为 0。
执行契约场景 87/87 均匹配冻结基线，实际执行次数为 0。冻结数据和静态检查通过，线上 Redis 启用仍待独立验收。
