# RAG 阶段 8V 时间与大小作用域完成日志

日期：2026-11-13

## 1. 结论

阶段 8V 已完成时间范围和文件大小范围表达的安全闭环。当前执行契约不能精确表达相对时间、绝对日期、
大小比较或大小区间，因此这类请求会保留 MOVE、DELETE、SHARE 等语义意图和可安全复用的实体，但不会
生成可执行 ActionPlan。用户把范围缩窄为明确文件名、后缀或名称谓词后，系统可以在多轮上下文中恢复为
现有受支持操作。

本阶段没有增加动作类型，没有扩大角色或动作 allowlist，没有修改生产配置，没有运行封存 holdout，也
没有确认或执行真实业务动作。生产仍保持 `admin-single + RAG_ADMIN + FOLDER_CREATE`。

## 2. 冻结矩阵与初始结果

冻结候选集共 24 cases、30 turns：

```text
unsupported-temporal-selector          6 cases
unsupported-size-selector              6 cases
supported-literal-and-consultation     6 cases
constraint-context-repair              6 cases / 12 turns
```

首次干净冻结行为测量：

```text
run                                  20261113092546
intent / exact                              10/30
operation                                   22/28
unsafe executable plans                         7
```

问题集中在三类：不受支持的时间或大小筛选被截断后生成可执行计划，时间/大小文字出现在文件名或目录名
时被误拦截，以及用户用明确对象修正范围后不能稳定恢复原操作。

## 3. 实现边界

新增独立 `UnsupportedTemporalSizeScopeGuard`，没有把时间和大小逻辑继续堆入通用意图识别器。它执行
以下边界：

- 先遮罩引号中的文件名、目录名和搜索词，避免把字面量误判为筛选条件；
- 识别昨天、本周、最近 N 天、指定日期前后等时间范围；
- 识别大于、小于、超过、零字节和大小区间等文件大小范围；
- 保留 MOVE、DELETE、SHARE 等语义意图以及安全的目标目录；
- 对不受支持的时间或大小选择器强制 response-only，不创建动作草稿或执行任务；
- 对明确文件名、后缀或名称谓词的后续修正清除旧范围条件并恢复现有操作；
- 避免把 `归档区`、`共享区` 等目录名误识别为额外动作；
- 支持“改成只为合同.pdf生成分享链接”等明确的分享范围缩窄。

`NonExecutableRequestGuard` 在识别前执行安全拦截，`ExecutableConstraintGuard` 只作为已有动作草稿的
后置防线；后者不再为时间或大小范围重新调用本地识别，从而避免改变原语义。

## 4. Candidate 验证与晋升

最终本地 candidate 行为测量：

```text
run                                  20261113094124
intent / exact                              30/30
operation                                   28/28
unsafe / executed                             0/0
```

该次本地报告用于提交前行为检查；提交 `33028b6` 后又执行三次干净在线 candidate。三次均为
30/30、operation 28/28、unsafe=0、executed=0：

```text
20261113094347       deepseek=8, local_fallback=22
20261113094408       deepseek=8, local_fallback=22
20261113094427       deepseek=8, local_fallback=22
```

候选一次性晋升到 development：

```text
dataset version                     rag_recognition_dev_v16
development cases / turns                 402 / 516
candidate cases                                0
duplicate case IDs                              0
```

## 5. 晋升后本地与在线 Development

干净本地兜底运行：

```text
run                                  20261113094706
source revision                          339f36d
sourceDirty                                  false
intent / exact                            516/516
operation                                 284/284
unsafe / executed                             0/0
sources                  local_fallback=515, local_context=1
```

三次正式有效在线全量运行：

```text
run 20261113100421   516/516   deepseek=255   local_guard=219   local_policy=15   local_structure=27
run 20261113101529   516/516   deepseek=255   local_guard=219   local_policy=15   local_structure=27
run 20261113102523   516/516   deepseek=258   local_guard=219   local_policy=12   local_structure=27
```

三轮均为 operation 284/284、unsafe=0、executed=0、source revision `339f36d`、sourceDirty=false；
没有 `local_unavailable` 或 `unclassified`，临时 Identity 会话均已注销。

稳定性聚合：

```text
runCount                                  3
totalTurns                              516
intentDriftCount                          0
operationDriftCount                       0
exactOutcomeDriftCount                    0
pathDriftCount                            3
alwaysExactCorrectCount                 516
fullyStableCount                        513
unsafe / unavailable                      0 / 0
```

3 项变化只发生在 `deepseek` 与 `local_policy` 之间，涉及既有集合语义样本；意图、操作和完整契约始终
一致。Phase 8V 新增时间、大小、字面量和上下文修正样本没有结果漂移。

## 6. 作废运行治理

`20261113095013` 在 DeepSeek 额度耗尽前完成部分在线识别，最终虽为 516/516、unsafe=0、executed=0，
但包含 165 个 `local_unavailable`，只作为降级安全证据，不进入在线基线或稳定性聚合。随后一次运行在
数据集开始前被 canary 以 `deepseek_http_402` 拦截，没有形成正式指标报告。

额度恢复后只执行上述三次必需的完整在线运行，没有继续增加探针或重复调用。没有通过降低来源要求、
改写报告或把本地兜底冒充在线结果来完成门禁。

## 7. Holdout 与错误分类治理

封存 holdout 未运行，也没有把 holdout 原句复制到开发集或规则。双基线继续携带来源提交 `d54eaa6`
的既有 holdout 证据。错误分类基线保持 1 个历史普通 `intent_routing`，并记录 3 个仅仲裁路径波动：

```text
availability_failure                    0
safety_violation                        0
sampling_outcome_variance               0
arbitration_path_variance               3
```

## 8. 完整回归

```text
RAG Java tests                         390，0 failures，0 errors
RAG Execution Java tests                56，0 failures，0 errors
Java 合计                              446
完整执行语义契约                       87/87，executed=0
契约 run                               20261113103713
契约 source revision                   de812dc，dirty=false
契约基线哈希                           bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查              通过
git diff --check                        通过
```

隔离评测只登记并取消未确认任务，没有确认或执行真实业务动作。生产服务、生产数据库、角色和动作
allowlist 均未改变。

## 9. 提交

```text
487c417 Freeze Phase 8V temporal and size scope matrix
33028b6 Guard unsupported temporal and size scopes
339f36d Promote Phase 8V temporal and size scope matrix
de812dc Refresh Phase 8V recognition baselines
```

用户原有的 `DEPLOYMENT_TENCENT_CLOUD.md` 删除状态、`.agents/` 和
`云盘国内环境部署上线.md` 均未进入任何提交。

## 10. 后续方向

阶段 8V 已闭环，可以进入下一独立识别优化节点。若未来产品要真正支持时间或大小筛选执行，必须单独
设计筛选时区、时间字段、单位换算、边界包含关系、集合快照、确认展示和执行契约，不应放宽当前门禁。
