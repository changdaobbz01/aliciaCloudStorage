# RAG 阶段 8O 对话识别语言族闭环日志｜2026-11-11

## 1. 结论

阶段 8O 已完成普通对话与非执行请求的第一批独立识别优化。工作从冻结、授权、合成且脱敏的表达矩阵开始，先测量再修改，并在发现在线标签波动后继续收紧边界，直到同一干净提交连续三轮无意图、操作或正确性漂移。

本轮没有扩大执行角色、动作白名单或生产发布范围。生产继续保持：

```text
stage                              admin-single
执行角色                           RAG_ADMIN
唯一动作                           FOLDER_CREATE
真实业务执行                       0
生产部署                           0
holdout 运行                       0
```

## 2. 数据矩阵与修改前证据

阶段开始时，development 有 193 turns，其中普通社交、无聊和用户身份等非执行语言族各只有 1 条代表样本。为避免围绕已有句子调参，新建 41 条无重复候选矩阵，覆盖：

- 社交、确认、闲聊、无聊；
- 助手身份与用户身份；
- 写作，以及包含危险操作引用的文案改写；
- 不支持的线下代办和外部资源；
- 产品信息、产品原因、产品反馈；
- 仅咨询的能力问句和连续多轮能力问句；
- 实体设备维修、金融预测、医疗诊断等范围外问题。

41 条候选全部标注为不可执行。修改前结果：

```text
在线 run 20261111051535            35/41，unsafe=0，executed=0
本地 run 20261111051726            11/41，unsafe=0，executed=0
```

这说明在线模型已有一定泛化能力，但本地兜底覆盖不足，且不能把在线成功当成本地能力。

## 3. 结构化实现与边界修复

本轮修改集中在三层：

1. 配置化意图规则补齐社交、确认、闲聊、身份、写作、代办、外部资源、产品和能力咨询的通用表达；
2. 语义模板增加各语言族的代表性示例；
3. `NonExecutableRequestGuard` 增加三类安全边界：
   - 引号内危险操作仅作为文案改写；
   - 实体设备维修明确回退；
   - 金融预测和医疗诊断等高风险专业请求明确回退。

实现过程中完整测试主动捕获并修复两项过匹配：

```text
“列出需求说明”                         不得误判为写作
包含“云盘/产品图”的明确移动语句          不得误判为产品介绍
```

对应规则已收窄，并加入明确文件命令反例。修改不是按 41 条原句逐项硬编码，而是扩展语言族并用正反例共同限定边界。

## 4. 晋升前双通道证据

结构修复后的候选结果：

```text
本地 run 20261111052343            41/41
在线 run 20261111052634            41/41，deepseek=33，local_fallback=8
```

同时复验原 193 条 development：

```text
在线 run 20261111052808           193/193，operation 112/112
本地 run 20261111053456           193/193，operation 112/112
```

41 条候选随后一次性晋升：

```text
development                       234 turns
candidate                           0 turns
production-probe                    9 turns
holdout                            29 turns（未运行）
```

代码与数据提交：

```text
7fdc4bb Expand conversation recognition language families
```

## 5. 三轮基线主动发现的波动

第一次正式在线三轮没有被直接记为成功。第 2 轮主动发现：

```text
run 20261111054835
dev.8o.capability.multiturn
expected                           assistant_capability_examples
actual                             assistant_execution_explanation
```

加入“连续/多轮上下文中的文件操作能力询问”窄守卫后，下一次正式在线轮又发现同一相邻标签簇的第二个波动：

```text
run 20261111055852
dev.8o.capability.consult_only
expected                           assistant_capability_examples
actual                             assistant_execution_explanation
```

最终增加两个仅覆盖能力询问的确定性守卫：

- 能否识别或理解连续、多轮上下文中的文件操作；
- 明确只咨询、不执行，并询问能够如何帮助。

明确的连续移动命令和“咨询完就执行删除”等反例仍不被守卫拦截。两次波动均只发生在相邻非执行标签间，`operation 112/112`、`unsafe=0`、`executed=0` 始终不变。

稳定性修复提交：

```text
19ac71b Stabilize multi-turn capability recognition
```

## 6. 干净提交正式基线

真实本地兜底使用无效隔离模型 key，确保没有把在线成功误记为本地能力：

```text
run 20261111060724
intent / exact                    234/234
operation                         112/112
unsafe / executed                  0 / 0
recognitionSources                local_fallback=234
sourceDirty                       false
```

同一干净提交、同一 development 数据集连续三轮在线结果：

```text
run 20261111060813       234/234，operation 112/112，deepseek=149，local_fallback=85
run 20261111061453       234/234，operation 112/112，deepseek=148，local_fallback=86
run 20261111062139       234/234，operation 112/112，deepseek=149，local_fallback=85
```

稳定性聚合：

```text
runCount                            3
totalTurns                        234
intentDriftCount                    0
operationDriftCount                 0
exactOutcomeDriftCount              0
pathDriftCount                      6
alwaysExactCorrectCount           234
fullyStableCount                  228
unsafe / unavailable                0 / 0
```

6 项变化只发生在 DeepSeek 与本地权威策略路径之间，最终意图、操作字段和完整标签契约均未漂移。

机器基线提交：

```text
92df0b5 Refresh phase 8O recognition baselines
```

holdout 文件未变，本轮继续携带 `d54eaa6` 上的封存证据，没有重复运行 holdout。既有在线 holdout 仍为 28/29，错误基线保留 1 项 `intent_routing`，并记录 6 项非阻断路径方差。

## 7. 完整回归

最终提交链上的验证：

```text
RAG Java tests                    355，0 failures，0 errors
RAG Execution Java tests          56，0 failures，0 errors
Java 合计                         411
完整执行语义契约                  87/87，executed=0
契约 run                          20261111062927
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

本轮隔离评测只登记并取消未确认任务，没有真实业务写入；临时 Identity 会话均已注销。

## 8. 后续建议

阶段 8O 已闭环，不应再围绕本轮 41 条表达反复增加规则。下一阶段应先收集新的授权、脱敏表达，再独立扩展测试矩阵，重点关注仍稀缺的多轮普通对话、上下文指代和产品边界。

生产不需要立即部署本轮修改。只有完成后续独立样本验收并形成明确发布窗口后，才考虑统一发布识别版本；生产 `admin-single + RAG_ADMIN + FOLDER_CREATE` 边界保持不变。
