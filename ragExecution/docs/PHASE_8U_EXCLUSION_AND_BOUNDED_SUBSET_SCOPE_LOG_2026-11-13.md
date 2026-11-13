# RAG 阶段 8U 排除、剩余与有界子集作用域完成日志

日期：2026-11-13

## 1. 结论

阶段 8U 已完成排除、剩余、数量、次序和有界子集表达的安全闭环。当前执行契约不能精确表达
Top-N、序号区间、随机 N 个、排除某项后的集合或补集，因此这些请求会保留用户动作意图和安全实体，
但不会生成 ActionPlan。用户把范围缩窄为明确文件名后，系统可以在多轮上下文中恢复为现有原子操作。

本阶段没有增加动作类型，没有扩大角色或动作 allowlist，没有修改生产配置，没有运行封存 holdout，也
没有确认或执行真实业务动作。生产仍保持 `admin-single + RAG_ADMIN + FOLDER_CREATE`。

## 2. 冻结矩阵与初始结果

冻结候选集共 24 cases、30 turns：

```text
unsupported-cardinality-selector      6 cases
unsupported-exclusion-remainder       6 cases
supported-positive-and-literal        6 cases
subset-context-repair                 6 cases / 12 turns
```

首次冻结行为测量：

```text
intent / exact                         6/30
operation                             21/29
unsafe executable plans                  9
```

问题集中在三类：不受支持的数量或区间被降级为单对象计划，排除条件被截断后生成正向集合计划，以及
集合计划缩窄为明确对象时旧筛选实体没有被完整清除。

## 3. 实现边界

新增独立 `UnsupportedSubsetScopeGuard`，没有继续扩张复合命令守卫。它执行以下边界：

- 先遮罩引号中的文件名、目录名和搜索词，避免把字面量误判为范围表达；
- 识别最近/最早/前后 N 个、序号区间、随机 N 个以及排除、保留、其余和剩余表达；
- 保留 MOVE、DELETE、SHARE 等语义和可安全复用的目标目录、后缀、类别或名称谓词；
- 对不受支持的选择器强制 response-only，不创建动作草稿或执行任务；
- 对明确文件名的后续修正清除旧集合筛选条件并恢复现有原子操作；
- “只移动第二个”等相对序号仍拒绝恢复，必须提供明确名称。

现有 `collection.move_by_category`、`collection.move_by_extension` 和
`collection.move_by_name_contains` 仅解除旧约束守卫的误拦截。这些动作在修改前已经存在，本阶段没有
新增 action 或改变生产 allowlist。

## 4. Candidate 验证与晋升

最终本地 candidate：

```text
run                                  20261113064600
intent / exact                              30/30
operation                                   29/29
unsafe / executed                             0/0
```

三次在线 candidate 均为 30/30、operation 29/29、unsafe=0、executed=0：

```text
20261113064730       deepseek=8, local_fallback=22
20261113064759       deepseek=8, local_fallback=22
20261113064821       deepseek=8, local_fallback=22
```

候选一次性晋升到 development：

```text
dataset version                     rag_recognition_dev_v15
development cases / turns                 378 / 486
candidate cases                                0
duplicate case IDs                              0
```

## 5. 晋升后本地与在线 Development

干净本地兜底运行：

```text
run                                  20261113091110
source revision                          80d4d88
sourceDirty                                  false
intent / exact                            486/486
operation                                 256/256
unsafe / executed                             0/0
sources                  local_fallback=485, local_context=1
```

三次正式有效在线全量运行：

```text
run 20261113082000   486/486   deepseek=251   local_guard=200   local_policy=11   local_structure=24
run 20261113084034   486/486   deepseek=249   local_guard=200   local_policy=13   local_structure=24
run 20261113090008   486/486   deepseek=249   local_guard=200   local_policy=13   local_structure=24
```

三轮均为 operation 256/256、unsafe=0、executed=0、source revision `80d4d88`、sourceDirty=false；
没有 `local_unavailable` 或 `unclassified`，临时 Identity 会话均已注销。

稳定性聚合：

```text
runCount                                  3
totalTurns                              486
intentDriftCount                          0
operationDriftCount                       0
exactOutcomeDriftCount                    0
pathDriftCount                            6
alwaysExactCorrectCount                 486
fullyStableCount                        480
unsafe / unavailable                      0 / 0
```

6 项变化只发生在 `deepseek` 与 `local_policy` 之间，意图、操作和完整契约始终一致。

## 6. 作废运行治理

以下运行没有进入任何基线或稳定性聚合：

- `20261113065339`、`20261113070344`、`20261113070622`：DeepSeek HTTP 402，包含
  `local_unavailable`，只证明 fail-safe；
- `20261113082952`：单个供应商响应超时，包含 1 个 `local_unavailable`；
- `20261113085026`：一个普通对话回合出现随机意图漂移，虽 unsafe=0，仍按准确率门禁作废。

没有通过降低来源要求、改写报告或把本地兜底冒充在线结果来完成门禁。

## 7. Holdout 与错误分类治理

封存 holdout 未运行，也没有把 holdout 原句复制到开发集或规则。双基线继续携带来源提交 `d54eaa6`
的既有 holdout 证据。错误分类基线保持 1 个历史普通 `intent_routing`，并记录 6 个仅仲裁路径波动：

```text
availability_failure                    0
safety_violation                        0
sampling_outcome_variance               0
arbitration_path_variance               6
```

## 8. 完整回归

```text
RAG Java tests                         385，0 failures，0 errors
RAG Execution Java tests                56，0 failures，0 errors
Java 合计                              441
完整执行语义契约                       87/87，executed=0
契约 run                               20261113091527
契约 source revision                   1dab575，dirty=false
契约基线哈希                           bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查              通过
git diff --check                        通过
```

隔离评测只登记并取消未确认任务，没有确认或执行真实业务动作。生产服务、生产数据库、角色和动作
allowlist 均未改变。

## 9. 提交

```text
2943037 Freeze Phase 8U subset scope matrix
23190a5 Guard unsupported subset scopes
80d4d88 Promote Phase 8U subset scope matrix
1dab575 Refresh Phase 8U recognition baselines
```

用户原有的 `DEPLOYMENT_TENCENT_CLOUD.md` 删除状态、`.agents/` 和
`云盘国内环境部署上线.md` 均未进入任何提交。

## 10. 后续方向

阶段 8U 已闭环，可以进入下一独立识别优化节点。若未来产品要真正支持 Top-N、排序区间、随机抽取、
排除或补集执行，必须单独设计排序依据、集合快照、一致性、确认展示和执行契约，不应放宽当前门禁。
