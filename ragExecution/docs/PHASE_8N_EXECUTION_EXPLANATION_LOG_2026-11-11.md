# RAG 阶段 8N 执行说明意图闭环日志｜2026-11-11

## 1. 结论

阶段 8N 已完成“识别与执行为何分离、为何必须确认、未确认或过期任务如何处理”等执行策略说明语义的独立建模，并完成本地兜底、在线模型、运行时任务登记/取消和数据库副作用审计。

本轮没有扩大执行角色、动作白名单或生产发布范围。生产继续保持：

```text
stage                              admin-single
执行角色                           RAG_ADMIN
唯一动作                           FOLDER_CREATE
真实业务执行                       0
生产部署                           0
holdout 运行                       0
```

## 2. 前置运行时链路闭环

在改识别能力前，先在旧干净提交 `a10edf6` 上连续完成三轮隔离运行时探针：

```text
run 20261111032117                10/10，operation 8/8，unsafe=0
run 20261111032149                10/10，operation 8/8，unsafe=0
run 20261111032212                10/10，operation 8/8，unsafe=0
```

三轮共登记并立即取消 18 个目录任务。数据库审计结果：

```text
executions                         18，全部 CANCELLED
steps                              18，attempts 总数 0
actions                            仅 FOLDER_CREATE
events                             1:EXECUTION_REGISTERED -> 2:EXECUTION_CANCELLED
Cloud receipts                     0
同名 storage_node                  0
非终态任务                         0
```

因此动态目录名、多轮补充和取消链路本身健康，不需要为运行时链路增加识别补丁。

## 3. 冻结候选与问题证据

建立 16 条独立候选矩阵：

- 12 条执行策略说明表达，覆盖直接修改、确认机制、识别/执行分离、未确认、过期和英文表达；
- 4 条边界反例，分别属于产品原因、产品反馈、能力示例和产品信息。

修改前在线测量：

```text
run 20261111032553                 4/16
unsafe                             1
```

其中“删除文件时为什么必须先让我确认”被误识别为 `file_delete`，证明需要在模型调用前建立不可执行的执行策略说明守卫。

## 4. 结构化实现

新增不可执行意图：

```text
assistant_execution_explanation
```

实现边界：

1. `NonExecutableRequestGuard` 在模型调用前识别执行策略说明；
2. 命中后固定返回 `RESPOND`，`actionDraft=none`，绝不创建可执行草稿；
3. 只覆盖“为什么确认/为什么不直接执行/未确认或过期会怎样”等说明语义；
4. 单项能力确认问句仍归 `assistant_capability_examples`，例如“如果要删除文件，是不是必须确认？”；
5. 产品原因、反馈、功能介绍继续保留各自边界；
6. persona、回复模板、UI 标签、DeepSeek 意图表、语义仲裁和验收场景同步更新；
7. 静态校验从 `intents.json` 读取合法意图，允许守卫专属意图而不要求伪造普通规则。

同时补齐三个已有的本地边界缺口：确认流程投诉、口语化“怎么帮我”和“支持哪些”。修改保持配置化和结构化，没有按候选原句堆叠执行规则。

## 5. 晋升前双通道证据

候选修复并完成边界收紧后的结果：

```text
在线 run 20261111034309            16/16，unsafe=0
在线 development 20261111034321   177/177，operation 112/112，unsafe=0
本地 development 20261111034821   177/177，operation 112/112，unsafe=0
本地 candidate 20261111034847     16/16，unsafe=0
```

数据晋升后：

```text
development                       193 turns
candidate                           0 turns
production-probe                    9 turns
holdout                            29 turns（未运行）
```

代码与数据提交：

```text
244e8f6 Separate execution policy explanations
```

## 6. 干净提交正式基线

真实本地兜底使用无效隔离模型 key，确保没有把在线成功误记为本地能力：

```text
run 20261111035113
intent / exact                    193/193
operation                         112/112
unsafe / executed                  0 / 0
recognitionSources                local_fallback=193
sourceDirty                       false
```

同一干净提交、同一 development 数据集连续三轮在线结果：

```text
run 20261111035255       193/193，operation 112/112，deepseek=118，local_fallback=75
run 20261111035752       193/193，operation 112/112，deepseek=119，local_fallback=74
run 20261111040253       193/193，operation 112/112，deepseek=119，local_fallback=74
```

稳定性聚合：

```text
runCount                            3
totalTurns                        193
intentDriftCount                    0
operationDriftCount                 0
exactOutcomeDriftCount              0
pathDriftCount                      6
alwaysExactCorrectCount           193
unsafe / unavailable                0 / 0
```

6 项变化只发生在 DeepSeek 与本地权威策略路径之间，最终意图、操作字段和完整标签契约均未漂移。

机器基线提交：

```text
f7ab6dd Refresh phase 8N recognition baselines
```

holdout 文件未变，本轮继续携带上一版本的封存证据，没有为了刷新 development 基线重复运行 holdout。既有在线 holdout 错误仍为 1 项 `intent_routing`，新增 6 项仅为非阻断路径方差。

## 7. 最终运行时探针与副作用审计

在包含新机器基线的干净提交 `f7ab6dd` 上完成最终运行时探针：

```text
run 20261111040920
intent / exact                      9/9
operation                           8/8
unsafe / executed                   0 / 0
recognitionSources                deepseek=8，local_fallback=1
sourceDirty                       false
```

本轮 6 个目录任务的只读数据库审计：

```text
executions                          6，全部 CANCELLED
attempts 总数                       0
错误动作                            0
事件顺序                            全部 REGISTERED -> CANCELLED
Cloud receipts                     0
同名 storage_node                  0
```

## 8. 完整回归

```text
RAG Java tests                    353，0 failures，0 errors
RAG Execution Java tests          56，0 failures，0 errors
Java 合计                         409
完整执行语义契约                  87/87，executed=0
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

## 9. 后续建议

阶段 8N 已闭环，不应再围绕这 16 条表达反复增加规则。下一阶段可开始独立的对话识别率工程：先扩展授权、脱敏、成组的表达矩阵，再按错误分类处理普通闲聊、产品边界和存储操作语义；执行策略说明继续由本轮不可执行守卫保护。

生产仍不需要部署本轮修改。只有在完成下一阶段验收并形成明确发布窗口后，才考虑将识别版本发布到生产。
