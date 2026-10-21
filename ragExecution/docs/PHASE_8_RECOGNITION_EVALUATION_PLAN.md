# RAG 阶段 8 识别能力评测与优化方案

## 1. 当前范围

阶段 8 与 RAG Execution 执行通道改造严格分离。生产继续保持
`admin-single + RAG_ADMIN + FOLDER_CREATE`，本阶段不扩大角色、动作 allowlist、批量或组合执行范围。

阶段 8A 先建立可重复的识别评测体系，只测量当前行为，不以修改规则或 Prompt 掩盖基线问题。

## 2. 两类门禁

现有 `test/rag-e2e/scenarios.json` 与 `stress-scenarios.json` 是执行语义契约门禁：

- 固定意图、实体、缺失槽位、SemanticFrame、ActionPlan 和安全状态。
- 完整套件必须保持 87 项全部通过。
- 任何核心语义漂移都由 `contract-baseline.json` 拒绝。

阶段 8 新增的识别数据集是准确率评测：

- `recognition-dev.json`：开发集，可以用于错误分类和定向优化。
- `recognition-holdout.json`：保留集，只在开发节点完成后评估，不根据单条失败即时调规则。
- 识别失败用于测量，不自动阻断命令；基础设施失败、清理失败和不安全可执行计划仍直接阻断。

两者不可相互替代。准确率提高不能以破坏 87 项执行契约为代价。

## 3. 标签与指标

标签策略版本为 `rag_recognition_labels_v1`。每个 turn 只有一个主意图标签；文件操作同时尽量标注
`semanticOperation`，非执行话语标注 `noExecutablePlan=true`。

每次评测记录：

- 主意图准确率。
- 已标注 turn 的语义操作准确率。
- 完整标签契约准确率。
- 按 group 分组的准确率。
- 期望意图到实际意图的混淆矩阵。
- DeepSeek 与本地兜底的识别来源数量。
- 缺失响应、基础设施失败和不安全可执行计划数量。

安全门槛固定为 `unsafeExecutableCount=0`，不能通过降低标签要求规避。

## 4. 数据集治理

- 标签描述产品期望，不复制当前实现输出。
- 开发集和保留集 case id 全局唯一；单轮测试语句不得重复。
- 新增数据先明确意图、上下文、风险和预期语义，再写入 JSON。
- 真实失败样本进入开发集前必须匿名化，不能保存 token、账号、真实文件路径或个人数据。
- 保留集只能在一次优化批次完成后运行；看到单条准确率失败后不得立即修改规则再反复刷分。若保留集触发不安全可执行计划，必须停止评测、将同类但不同措辞的样本提升到开发集，并按安全缺陷处理。
- 数据集、Prompt、模型名、评测来源和报告 runId 必须可追溯。

## 5. 运行方式

```powershell
pwsh -File test/rag-e2e/start-local.ps1
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite recognition-dev

# 一个开发批次完成后再运行
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite recognition-holdout

# 始终保留执行契约回归
pwsh -File test/rag-e2e/run-evaluation.ps1 -Suite all
pwsh -File test/rag-e2e/stop-local.ps1
```

原始响应和指标报告仅写入 Git 忽略的 `test/rag-e2e/results/`。普通识别评测不会确认任务；本轮影子登记的未确认任务会在每个 case 结束后取消。

## 6. 优化批次顺序

1. 冻结当前本地兜底基线和在线模型基线，识别来源必须分开统计。
2. 仅使用开发集建立错误分类：路由冲突、参数抽取、否定、安全边界、多轮上下文、模型不可用。
3. 每个批次只修改一个主要层面：规则、Prompt、检索或上下文，不混合大改。
4. 运行开发集、Java 回归和 87 项契约门禁。
5. 开发节点稳定后运行一次保留集，记录泛化变化。
6. 达到发布门槛后才准备候选版本；生产更新仍需独立批准和验收。

## 7. 进入后续优化的门槛

- 评测脚本、数据集和标签策略通过静态校验。
- 本地兜底开发集与保留集各形成一份可复现的当前基线。
- DeepSeek 可用时另行形成在线模型基线，不能用本地兜底结果冒充模型准确率。
- `unsafeExecutableCount=0`、`infrastructureFailures=0`。
- 87 项执行语义契约继续全部通过。

在上述条件完成前，不开始生产 Prompt 更新或执行权限扩容。

阶段 8A 的首版本地兜底基线已于 2026-10-21 完成，结果与发现的问题记录在
[阶段 8A 识别评测基线日志](PHASE_8A_RECOGNITION_BASELINE_LOG_2026-10-21.md)。DeepSeek 在线基线尚未建立。

阶段 8B 第一批词汇覆盖和路由优先级优化也已于 2026-10-21 完成。开发集完整契约由
46/59 提升到 54/59，保留集保持 19/29，未发生安全或执行契约退化。详细范围、证据和阶段 8C
结构化语义清单见
[阶段 8B 第一批路由优化日志](PHASE_8B_FIRST_ROUTING_OPTIMIZATION_LOG_2026-10-21.md)。

阶段 8C 结构化语义修复已于 2026-10-21 完成。开发集完整契约达到 59/59，保留集保持
19/29，`unsafeExecutableCount=0`，87 项执行语义契约保持不变。阶段 8D 应建立独立在线模型基线并从
匿名化真实样本扩充开发集，不按现有保留集的单条失败反复调规则。详细安全设计与验证证据见
[阶段 8C 结构化语义修复日志](PHASE_8C_STRUCTURAL_SEMANTICS_LOG_2026-10-21.md)。

阶段 8D 的在线基线门禁已于 2026-10-21 完成：评测在数据集开始前使用无执行能力的安全 canary
验证真实在线模型成功证据；指标将 `deepseek`、`local_guard`、`local_structure`、`local_policy`、
`local_unavailable` 和 `unclassified` 分开统计；在线基线拒绝任何模型不可用或无法分类的结果。
当前 canary 收到 DeepSeek HTTP 402，因此流程按设计在写报告和运行数据集前失败关闭。开发集和
保留集没有被本地兜底结果冒充为在线基线。恢复账号余额或换入有效密钥后，先重跑开发集 canary，
通过后才允许按治理规则运行一次保留集。详细证据和恢复命令见
[阶段 8D 在线基线就绪日志](PHASE_8D_ONLINE_BASELINE_READINESS_LOG_2026-10-21.md)。

DeepSeek 账号可用性恢复后，阶段 8D 在线基线已在干净提交 `8f47ace` 上完成。开发集完整契约
59/59，保留集 25/29，`unsafeExecutableCount=0`，在线与本地回退指标分别保存。单次开发集曾出现
58/59，说明在线模型仍存在采样波动；下一阶段应先测量重复运行稳定性并按错误类别优化，不用保留集
单条样本驱动规则。完整结果见
[阶段 8D 在线基线日志](PHASE_8D_ONLINE_BASELINE_LOG_2026-10-21.md)。
