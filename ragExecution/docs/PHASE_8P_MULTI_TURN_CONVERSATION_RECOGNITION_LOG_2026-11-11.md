# RAG 阶段 8P 多轮对话连续性闭环日志｜2026-11-11

## 1. 结论

阶段 8P 已完成多轮普通对话、身份、产品、写作、能力边界和高风险范围外请求的连续性识别优化。
本轮先冻结 24 个授权、合成且脱敏的多轮场景，再分别测量本地兜底与 DeepSeek 在线通道；修复集中在
通用上下文关系和响应型边界，没有按单句堆叠特例。

最终 development 为 258 cases、283 turns，其中 24 cases、49 turns 是本轮多轮样本；candidate 已清空。
同一干净代码提交上的本地基线和三次在线全量运行均达到 283/283，语义操作 120/120，
`unsafe=0`、`executed=0`。三次在线结果没有意图、操作或完整契约结果漂移。

本轮没有扩大角色、动作白名单或生产发布范围。生产继续保持：

```text
stage                              admin-single
执行角色                           RAG_ADMIN
唯一动作                           FOLDER_CREATE
真实业务执行                       0
生产部署                           0
holdout 运行                       0
```

## 2. 数据矩阵与修改前证据

阶段开始前，development 为 234 turns。新建并冻结 24 个多轮候选场景、49 turns，覆盖：

- 社交转闲聊、闲聊结束和无聊后的自然延续；
- 用户身份到助手身份、助手身份到能力边界；
- 产品信息到产品原因、反馈上下文延续；
- 文案继续润色，以及包含危险操作引用的文案延续；
- 外部资源替代方案、线下代办指导边界；
- 能力咨询后的确认，不误登记执行；
- 金融预测和医疗建议等高风险请求的持续拒答；
- 闲聊与搜索、移动与闲聊、产品与搜索等执行/非执行切换；
- 未完成创建的取消、重命名槽位补充和无依据指代。

修改前结果：

```text
在线 run 20261111064556            41/49，failed=8，unsafe=2
                                      deepseek=40，local_fallback=9
本地 run 20261111064745            28/49，failed=21，unsafe=3
                                      local_fallback=49
```

这组结果表明问题不只是词汇覆盖不足：单轮识别结果在进入下一轮后缺少明确的响应型连续性边界，
部分省略表达还会被文件候选上下文重新解释为可执行动作。

## 3. 结构化修复

本轮新增 `ResponseOnlyConversationFollowUpGuard`，只处理能够从已保存会话状态确定的响应型多轮关系。
它在模型调用和通用非执行守卫之前执行，并统一生成不可执行响应。主要关系包括：

```text
social -> personal chat
user identity -> assistant identity
assistant identity -> capability
product information -> product reason / feedback
writing -> writing refinement
external resource / errand -> supported guidance boundary
capability consultation -> acknowledgement
high-stakes request -> persistent fail-closed fallback
```

同时完成以下边界修复：

1. `AssistantConversationService` 不再让文件候选上下文覆盖响应型多轮守卫结果；
2. `NonExecutableRequestGuard` 补齐引用文案、医疗高风险、产品文件能力、助手组织能力、外部云资源和取消语义；
3. 意图规则补齐相邻语言族，但执行动作仍由原有权威语义契约决定；
4. 新增正反例测试，保护明确文件命令、槽位补充、取消和上下文切换边界。

行为与数据提交：

```text
fb507b7 Add multi-turn recognition continuity
```

## 4. 候选验收与晋升

修复后的冻结候选在双通道全部通过：

```text
在线 run 20261111070729            49/49，operation 8/8
                                      unsafe=0，executed=0
本地 run 20261111070835            49/49，operation 8/8
                                      unsafe=0，executed=0
```

随后一次性晋升，未保留重复或未审查样本：

```text
development                       258 cases / 283 turns
multi-turn                         24 cases / 49 turns
candidate                           0 cases / 0 turns
重复规范化消息                      0
holdout                            29 turns（未运行）
```

## 5. 干净提交正式基线

真实本地兜底基线：

```text
run 20261111072333
intent / exact                    283/283
operation                         120/120
unsafe / executed                  0 / 0
recognitionSources                local_fallback=283
sourceRevision                    fb507b7
sourceDirty                       false
```

同一干净提交、同一 development 数据集的三次合格在线全量运行：

```text
run 20261111082120       283/283，deepseek=174，local_fallback=109
run 20261111082832       283/283，deepseek=171，local_fallback=112
run 20261111084034       283/283，deepseek=171，local_fallback=112
```

三轮均为 operation 120/120、unsafe=0、executed=0、sourceDirty=false，且只包含
`deepseek`、`local_guard`、`local_structure` 和 `local_policy` 四类允许路径。

稳定性聚合：

```text
runCount                            3
totalTurns                        283
intentDriftCount                    0
operationDriftCount                 0
exactOutcomeDriftCount              0
pathDriftCount                      5
alwaysExactCorrectCount           283
fullyStableCount                  278
unsafe / unavailable                0 / 0
```

5 项变化只发生在 DeepSeek 直出与本地权威仲裁路径之间，最终意图、操作和完整标签契约均未变化。

holdout 文件继续封存。本轮携带 `d54eaa6` 上的既有证据，没有为了更新来源提交而重跑：

```text
本地 holdout                      22/29
在线 holdout                      28/29
既有准确性偏差                     intent_routing=1
新增阻断级错误                     0
```

## 6. 严格门禁与额度故障处理

本轮没有把“最终标签正确”直接等同于“在线模型可用”。早期干净运行 `20261111072438` 在修正评测路径
分类后仍包含 21 条真实 `local_unavailable`，因此没有进入在线基线或三轮稳定性聚合。另一次 HTTP 402
发生在 canary 阶段，数据集未开始执行，也没有产生半成品基线。

评测器原先没有识别新守卫的 `conversation_follow_up_guard:` 前缀，曾把 13 条模型调用前的合法本地守卫
误标为 `local_unavailable`。本轮修正分类器，并从已保存的原始报告重新计算指标，没有通过修改报告内容
或放宽门禁掩盖真实上游故障。

```text
3ebfb09 Classify conversation follow-up guard evidence
113398a Refresh Phase 8P recognition baselines
```

## 7. 完整回归

最终行为提交上的验证：

```text
RAG Java tests                    360，0 failures，0 errors
RAG Execution Java tests           56，0 failures，0 errors
Java 合计                         416
完整执行语义契约                  87/87，executed=0
契约 run                          20261111072013
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

隔离评测只登记并取消未确认任务，没有真实业务写入；所有临时 Identity 会话均已注销。用户原有的
`DEPLOYMENT_TENCENT_CLOUD.md` 删除状态、`.agents/` 和 `云盘国内环境部署上线.md` 均未进入提交。

## 8. 后续建议

阶段 8P 已闭环，不应继续围绕这 49 个多轮 turns 反复加规则。下一阶段应单独建立新的授权、脱敏表达
采集与识别率优化批次，优先增加真实使用风格下的长对话、跨主题指代、纠错、反悔和中断恢复样本；
仍然坚持候选冻结、双通道测量、一次性晋升和封存 holdout。

本轮修改无需立即部署生产。进入发布窗口前应统一复核 RAG、RAG Execution 与 Android 调用契约；
生产 `admin-single + RAG_ADMIN + FOLDER_CREATE` 边界保持不变。
