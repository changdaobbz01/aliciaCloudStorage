# RAG 阶段 8Q 上下文纠错与中断恢复闭环日志｜2026-11-11

## 1. 结论

阶段 8Q 已完成多轮会话中的纠错、取消、反悔、跨主题中断恢复和陈旧指代安全边界优化。
本轮使用 24 个授权、合成且脱敏的场景、71 个 turns，先冻结标签再测量和修复；没有把主动模拟
描述为匿名真实流量，也没有重复使用阶段 8P 的样本调参。

最终 development 为 282 cases、354 turns，candidate 已清空。同一干净行为提交上的本地基线
和三次 DeepSeek 在线全量运行均达到 354/354，语义操作 151/151，`unsafe=0`、`executed=0`。
三次在线运行没有意图、操作或完整契约结果漂移。

本轮没有生产部署，没有扩大角色或动作白名单，生产边界继续保持：

```text
stage                              admin-single
执行角色                           RAG_ADMIN
唯一动作                           FOLDER_CREATE
真实业务执行                       0
holdout 运行                       0
```

## 2. 冻结矩阵与修改前证据

冻结候选固定为 24 cases、71 turns，覆盖：

- 长对话中的非执行意图切换；
- 搜索、重命名、创建、移动和产品反馈纠错；
- 删除、移动、创建、批量移动和上传的取消/反悔；
- 跨主题中断、显式恢复和陈旧指代；
- 执行解释、引用命令、外部资源和高风险请求的安全切换。

修改前真实本地兜底结果：

```text
run 20261111091653                39/71
failed                            32
```

问题集中在三类结构：活动操作缺少可恢复的权威焦点、纠错信息不能稳定覆盖旧槽位，以及响应型话题
结束后陈旧的“它/那个/照做”仍可能被模型解释为继续动作。

## 3. 结构化修复

本轮新增 `ContextualOperationFollowUpGuard`，并让会话焦点显式保存可中断操作。守卫只在已有权威
上下文能够确定关系时处理纠错或恢复；无法确定对象、操作或活动上下文时统一 fail-closed。

主要规则如下：

```text
同一活动意图中的明确纠错            新信息覆盖旧槽位
明确取消或“先不要”                  终止待处理动作
取消后给出完整新命令                建立新的动作意图
跨主题后明确恢复并补足对象            恢复原动作
跨主题后只有陈旧指代                fail-closed
解释、引用或能力咨询后的模糊执行       fail-closed
```

同时完成以下改进：

1. 搜索、重命名、创建和移动统一使用结构化上下文恢复，而不是按原句堆叠特例；
2. 取消和过期上下文不再被后续模糊确认重新激活；
3. `.log` 等扩展名集合移动、产品/写作/外部资源边界得到补正；
4. 在线模型前增加陈旧执行提示的 fail-closed 门禁；
5. 离线评测栈增加显式模型开关，保证本地兜底基线真实可复现。

行为与数据提交：

```text
756843f Freeze Phase 8Q context recovery matrix
753cc9d Add offline recognition stack switch
1dd9775 Correct Phase 8Q rename relation label
33a9e8b Harden contextual recognition recovery
417b443 Fail closed on stale execution prompts
c8836e5 Promote Phase 8Q recognition matrix
```

## 4. 候选验收与晋升

本地候选最终结果：

```text
run 20261111093739                71/71
operation                         31/31
unsafe / executed                  0 / 0
```

第一次在线候选验收 `20261111093436` 为 68/71。三条失败都是非执行解释或写作上下文后的陈旧确认，
没有登记可执行动作，也没有产生 unsafe；据此补上通用 fail-closed 门禁。最终在线候选结果：

```text
run 20261111093938                71/71
operation                         31/31
unsafe / executed                  0 / 0
deepseek / local_fallback          25 / 44
local_context                       2
```

随后一次性晋升：

```text
development                       282 cases / 354 turns
Phase 8Q                           24 cases / 71 turns
candidate                           0 cases / 0 turns
holdout                            29 turns（未运行）
```

## 5. 正式基线与在线稳定性

干净行为提交 `c8836e5` 上的真实本地兜底基线：

```text
run 20261111094228
intent / exact                    354/354
operation                         151/151
unsafe / executed                  0 / 0
recognitionSources                local_fallback=354
sourceDirty                       false
```

同一提交、同一 development 数据集的三次在线全量运行：

```text
run 20261111094345       354/354，deepseek=191，local_fallback=161，local_context=2
run 20261111095132       354/354，deepseek=190，local_fallback=162，local_context=2
run 20261111095924       354/354，deepseek=191，local_fallback=161，local_context=2
```

三轮均为 operation 151/151、unsafe=0、executed=0、sourceDirty=false，临时 Identity 会话均已注销。
稳定性聚合：

```text
runCount                            3
totalTurns                        354
intentDriftCount                    0
operationDriftCount                 0
exactOutcomeDriftCount              0
pathDriftCount                      6
alwaysExactCorrectCount           354
fullyStableCount                  348
unsafe / unavailable                0 / 0
```

6 项变化只发生在 DeepSeek 直出与本地权威仲裁路径之间，最终意图、操作和完整契约均未变化。

评测器最初把 `local_context` 的两条安全澄清标为 `unclassified`。原始响应的实际标签和契约均正确，
因此只补齐评测路径映射，并用官方测量脚本从三份原始报告重新计算；没有重跑模型、修改原始报告或
放宽门禁。基线提交：

```text
6460d78 Refresh Phase 8Q recognition baselines
```

holdout 继续封存。本轮携带 `d54eaa6` 上的既有证据：本地 22/29、在线 28/29，既有
`intent_routing=1`，新增阻断级错误为 0。

## 6. 完整回归

最终验证结果：

```text
RAG Java tests                    363，0 failures，0 errors
RAG Execution Java tests          56，0 failures，0 errors
Java 合计                         419
完整执行语义契约                  87/87，executed=0
契约 run                          20261111101049
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

隔离评测仅登记并取消未确认任务，没有真实业务写入。用户原有的 `DEPLOYMENT_TENCENT_CLOUD.md`
删除状态、`.agents/` 和 `云盘国内环境部署上线.md` 均未进入提交。

## 7. 后续建议

阶段 8Q 已闭环，不应继续围绕这 71 个 turns 反复加规则。下一阶段应建立新的授权、脱敏表达采集批次，
重点覆盖真实使用风格下的长对话和新的歧义类型；继续坚持先冻结候选、双通道测量、一次性晋升和
封存 holdout。

本轮修改无需立即部署生产。进入发布窗口前应统一复核 RAG、RAG Execution 与 Android 调用契约；
生产 `admin-single + RAG_ADMIN + FOLDER_CREATE` 边界保持不变。
