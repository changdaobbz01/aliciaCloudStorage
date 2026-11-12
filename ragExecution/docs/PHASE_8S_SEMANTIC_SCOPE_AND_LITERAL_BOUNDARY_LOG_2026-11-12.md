# RAG 阶段 8S 语义作用域与字面量边界闭环日志｜2026-11-12

## 1. 结论

阶段 8S 已完成字面量数据、假设/咨询语气、局部否定和显式上下文切换的独立评测与安全闭环。
本轮先冻结 24 个授权、合成且脱敏的场景、32 个 turns，再测量原行为和修改通用结构层；没有读取
生产用户表达，也没有把主动模拟描述为匿名真实流量。

最终 development 为 330 cases、426 turns，candidate 已清空。同一干净行为提交上的本地全量结果和
三次有效 DeepSeek 在线全量结果均为 426/426，语义操作 199/199，`unsafe=0`、`executed=0`；三次
在线运行的意图、操作和完整契约结果漂移均为 0。

本轮没有生产部署，没有扩大角色或动作白名单，生产边界保持不变：

```text
stage                              admin-single
执行角色                           RAG_ADMIN
唯一动作                           FOLDER_CREATE
真实业务执行                       0
holdout 运行                       0
```

## 2. 冻结矩阵与修改前证据

冻结候选固定为 24 cases、32 turns：

- 6 个把“删除、移动、确认”等危险动作词作为文件名、目录名或文档标题的字面量数据边界；
- 6 个能力咨询、假设执行、执行时机、前置条件、延后执行和流程解释的非执行边界；
- 6 个否定旧动作后明确提出新动作、替换参数和最终停止的局部否定边界；
- 6 个从咨询/写作切换到明确操作、取消后模糊继续、能力说明后明确重命名的上下文边界。

修改前本地结果：

```text
run                                20261112073337
source revision                    81c91c5
intent / exact                     20/32 / 20/32
operation                          14/19
unsafe / executed                  0 / 0
```

主要缺口是引号内动作词会抢占外层意图、咨询/延后表达可能被当成执行、否定旧子句会吞掉后续肯定
替代动作，以及取消或解释后的模糊继续可能继承陈旧上下文。

## 3. 通用实现修复

修改集中在可复用的语义边界，没有为候选原句建立逐句白名单：

1. 规则匹配前屏蔽引号中的字面量动作词，但实体提取继续使用原始文本；
2. 对明确的“文件名包含”搜索建立外层结构优先级，避免名称内容被解释成动作；
3. 统一能力咨询、执行时机、前置条件、延后执行、流程解释和显式停止的模型前非执行门禁；
4. 将否定限定在旧子句，允许后续明确肯定的新动作，同时继续拒绝“双重不要”的执行；
5. 只有同一表达存在明确文件名或引用名称时，代词才可绑定当前操作；
6. 扩充取消表达，并让没有真实待确认操作的“继续、那继续”等模糊确认 fail-closed；
7. 保留正向边界：存在真实待确认操作时，相同确认表达仍可进入正常确认链；
8. 使用不同于候选矩阵的正反单元测试，并以会在模型调用时失败的替身证明安全门禁先于模型。

最终 RAG Java 测试为 372 项，全部通过。

## 4. Candidate 验收与晋升

本地修复后的候选结果：

```text
run                                20261112074100
intent / exact                     32/32 / 32/32
operation                          19/19
unsafe / executed                  0 / 0
```

第一次干净在线 candidate 暴露了一个剩余问题：移动操作后的流程解释把随后“那继续吧”错误继承为
`assistant_execution_explanation`，而不是无待确认操作时安全回落。该结果未生成后端执行引用，也没有
真实执行：

```text
run                                20261112074452
intent / exact                     31/32 / 31/32
operation                          18/19
unsafe / executed                  0 / 0
```

提交 `4d5b743` 将模糊继续纳入无待确认操作门禁，并以真实待确认操作的正例保护正常确认链。最终干净
在线 candidate 为：

```text
run                                20261112074653
source revision                    4d5b743
intent / exact                     32/32 / 32/32
operation                          19/19
unsafe / executed                  0 / 0
deepseek / local_fallback          18 / 14
```

随后一次性晋升：

```text
development                       330 cases / 426 turns
Phase 8S                           24 cases / 32 turns
candidate                           0 cases / 0 turns
holdout                            29 turns（未运行）
```

晋升前后逐对象校验确认原 306 个 development cases 未改变；24 个 candidate 仅将 ID 前缀从
`candidate.8s` 改为 `dev.8s`，消息、标签和安全期望完全等价，ID 无重复。

## 5. 最终本地、在线与稳定性基线

干净行为与数据提交：

```text
1614e82 Promote Phase 8S semantic boundary matrix
```

本地全量结果：

```text
run                                20261112074935
intent / exact                     426/426
operation                          199/199
unsafe / executed                  0 / 0
recognitionSources                 local_fallback=425, local_context=1
sourceDirty                        false
```

三次正式有效在线全量运行：

```text
run 20261112075221       426/426，deepseek=234，local_fallback=190，local_context=2
run 20261112080204       426/426，deepseek=231，local_fallback=193，local_context=2
run 20261112094746       426/426，deepseek=232，local_fallback=192，local_context=2
```

三轮均为 operation 199/199、unsafe=0、executed=0、sourceDirty=false，临时 Identity 会话均已注销。
稳定性聚合：

```text
runCount                            3
totalTurns                        426
intentDriftCount                    0
operationDriftCount                 0
exactOutcomeDriftCount              0
pathDriftCount                      7
alwaysExactCorrectCount           426
fullyStableCount                  419
unsafe / unavailable                0 / 0
```

7 项变化只发生在 DeepSeek 直出与本地权威仲裁路径之间，最终意图、操作和完整契约均未变化。

连续第三次初始尝试 `20261112081149` 遇到 DeepSeek HTTP 402。评测仍通过安全本地降级保持 426/426、
unsafe=0、executed=0，但产生 190 个 `local_unavailable`，稳定性聚合器按设计拒绝将其作为在线证据。
该结果未被重跑掩盖，也未进入正式三轮；账号恢复后只补跑一次有效第三轮 `20261112094746`。

## 6. Holdout 与错误分类治理

holdout 文件未变化，本轮继续携带提交 `d54eaa6` 上的封存证据，没有为刷新 development 提交号而
重复运行：

```text
本地 holdout                      intent 23/29，operation 12/17，exact 22/29
在线 holdout                      intent 28/29，operation 16/17，exact 28/29
既有错误                          intent_routing=1
新增阻断错误                      0
```

新的错误分类基线记录 7 个仅仲裁路径波动，`availability_failure=0`、`safety_violation=0`、
`sampling_outcome_variance=0`。HTTP 402 的无效轮次没有混入基线。

## 7. 完整回归

```text
RAG Java tests                    372，0 failures，0 errors
RAG Execution Java tests           56，0 failures，0 errors
Java 合计                         428
完整执行语义契约                  87/87，executed=0
契约 run                          20261112081646
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

隔离评测只登记并取消未确认任务，所有可登记场景均为 `attempts=0`，没有真实业务写入。生产服务、
生产数据库、角色和动作 allowlist 均未改变。

用户原有的 `DEPLOYMENT_TENCENT_CLOUD.md` 删除状态、`.agents/` 和
`云盘国内环境部署上线.md` 均未进入任何提交。

## 8. 提交

```text
81c91c5 Freeze Phase 8S semantic boundary matrix
61eb958 Harden RAG semantic scope boundaries
4d5b743 Fail closed on ambiguous continuation
1614e82 Promote Phase 8S semantic boundary matrix
551ef0e Refresh Phase 8S recognition baselines
```

## 9. 后续建议

阶段 8S 已闭环，不应继续围绕这 32 个 turns 添加规则。下一阶段应另建并预先冻结独立矩阵，优先覆盖
新的语义结构或在产品上线后收集明确授权且完成脱敏的真实表达；继续坚持 candidate 双通道验收、
一次性晋升、三轮在线 development 稳定性和 holdout 封存。生产执行范围仍需独立审批，不随识别优化
自动扩大。
