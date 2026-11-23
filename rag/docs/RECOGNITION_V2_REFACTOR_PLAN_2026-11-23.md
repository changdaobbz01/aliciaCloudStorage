# RAG Recognition V2 对话识别改造计划

日期：2026-11-23
状态：T0 基线冻结已完成
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
| T1 | 未开始 | 识别链路可观测性 | 单次失败可定位到具体决策层，敏感字段为 0 |
| T2 | 未开始 | 授权反馈与失败样本闭环 | trace 反馈和授权样本分离，candidate 可复现 |
| T3 | 未开始 | Redis 持久会话 | 重启、token 更新和多实例不丢上下文 |
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

T0 不执行生产部署。T0 完成后进入 T1，先实现内存中的结构化 trace 和脱敏单元测试，再决定 trace 的持久化
方式与管理员查询入口；不得在没有脱敏门禁前把原始对话或模型 Prompt 写入数据库或日志。
