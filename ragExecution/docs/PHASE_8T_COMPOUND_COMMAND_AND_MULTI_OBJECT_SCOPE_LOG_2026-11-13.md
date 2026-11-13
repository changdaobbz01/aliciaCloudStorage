# RAG 阶段 8T 复合命令与多对象作用域闭环日志｜2026-11-13

## 1. 结论

阶段 8T 已完成复合命令、多对象作用域和后续范围修复的独立评测与安全闭环。系统不再把不受支持的
多动作命令静默截断为其中一个可执行动作；同一动作包含多个明确对象时可以保留用户意图，但不会在
客户端契约只能绑定单对象时生成一个被截断的计划。唯一公开支持的“新建目录后上传本地文件”组合仍
保持正常。

本轮没有修改生产配置、角色或动作 allowlist，没有运行封存 holdout，没有执行真实业务动作。生产仍
保持 `admin-single + RAG_ADMIN + FOLDER_CREATE`。

## 2. 冻结矩阵与修改前证据

候选在测量前冻结为 24 cases、30 turns：

```text
unsupported-compound-command       6 cases
multi-object-scope                 6 cases
single-action-conjunction          6 cases
compound-context-repair            6 cases / 12 turns
```

修改前本地运行 `20261113025646`：

```text
intent                             14/30
operation                          14/28
exact                              10/30
unsafeExecutableCount                 14
executed                               0
```

主要缺陷不是一般准确率偏差，而是不受支持的复合动作和多对象表达会被静默选成一个可执行计划。由于
这会让确认界面展示的范围小于用户原句，按安全缺陷处理并阻断晋升。

## 3. 通用修复

提交 `bb837d5` 在模型调用前增加统一的复合变更作用域门禁，并完成以下边界：

1. 先遮蔽引号、路径和重命名目标中的字面量，再判断动作和对象基数，避免把名称内连接词当作命令；
2. 除 `folder_create_then_upload` 外，多种不同变更动作组合统一 fail-closed；
3. 多文件移动、删除和分享保留对应意图与操作，但只返回说明，不生成被截断的 ActionPlan；
4. 双重命名映射、两个独立集合操作和无上下文多序号统一要求缩窄范围；
5. 局部取消只移除被否定动作，保留明确肯定动作；全部取消清除整个组合；
6. 多轮缩窄可以保留安全实体上下文，但响应型结果仍没有可执行计划；
7. 引号内危险词、路径片段、目标名称修正和“查找并告知位置”等单动作正例不被复合门禁误伤。

实现没有加入候选原句的逐句映射。不同措辞的正反单元测试证明安全门禁先于模型，并保护唯一支持组合、
单动作连接词和取消后的明确替代动作。

## 4. Candidate 验收与一次性晋升

最终候选证据：

```text
本地 run                           20261113031617
intent / exact                     30/30 / 30/30
operation                          28/28
unsafe / executed                  0 / 0
recognitionSources                 local_fallback=30

在线 run                           20261113031656
intent / exact                     30/30 / 30/30
operation                          28/28
unsafe / executed                  0 / 0
recognitionSources                 deepseek=9, local_fallback=21
source revision                    bb837d5
source dirty                       false
```

随后提交 `ed61cbb` 一次性晋升矩阵：

```text
development                        354 cases / 456 turns
Phase 8T                            24 cases / 30 turns
candidate                            0 cases / 0 turns
holdout                             29 turns（未运行）
```

机器校验确认原 330 个 development cases 逐对象未改变；24 个 candidate 只把 ID 前缀从
`candidate.8t` 改为 `dev.8t`，消息、标签和安全期望完全等价，晋升后 ID 无重复。

## 5. 全量回归发现与修复

晋升后的第一次本地 development `20261113032605` 没有不安全计划，但只达到 454/456：

```text
intent / exact                     454/456 / 454/456
operation                          225/227
unsafe / executed                  0 / 0
```

全量矩阵发现两个 candidate 未覆盖的既有正向边界：同一句修正移动目标目录，以及取消删除后明确改为移动。
通用门禁把“目标目录改成”或“改为把它移到”中的 `改成/改为` 误当成第二个重命名动作。

提交 `c36931e` 只收窄重命名动作判定：目标目录修正不计为重命名；`改为` 后跟移动、归档、放进或转移
等明确动作时按替代动作处理。不同措辞单元测试加入同一通用边界。修复后定向运行
`20261113032953` 为 2/2，随后完整 development 恢复为 456/456。候选标签和数据集没有为修复而改写。

## 6. 最终本地、在线与稳定性基线

最终本地全量：

```text
run                                20261113033007
intent / exact                     456/456 / 456/456
operation                          227/227
unsafe / executed                  0 / 0
recognitionSources                 local_fallback=455, local_context=1
source revision                    c36931e
source dirty                       false
```

三次正式有效在线全量运行：

```text
run 20261113033144       456/456，deepseek=242，local_fallback=212，local_context=2
run 20261113034101       456/456，deepseek=241，local_fallback=213，local_context=2
run 20261113035003       456/456，deepseek=242，local_fallback=212，local_context=2
```

三轮均为 operation 227/227、unsafe=0、executed=0、sourceDirty=false，临时 Identity 会话均已注销。
逐 turn 稳定性聚合：

```text
runCount                               3
totalTurns                           456
intentDriftCount                       0
operationDriftCount                    0
exactOutcomeDriftCount                 0
pathDriftCount                         6
alwaysExactCorrectCount              456
fullyStableCount                     450
unsafe / unavailable                   0 / 0
```

6 项变化只发生在 `deepseek` 与 `local_policy` 之间，意图、操作和完整契约始终一致。提交 `389d0c0`
刷新本地、在线、稳定性和错误分类机器基线。

## 7. Holdout 与错误分类治理

封存 holdout 未运行，也没有把 holdout 原句复制到开发集或规则。基线继续携带来源提交 `d54eaa6` 的
既有证据：在线 holdout 28/29、unsafe=0，唯一历史偏差仍为普通 `intent_routing`。新的错误分类基线
记录 6 个仅仲裁路径波动；`availability_failure=0`、`safety_violation=0`、
`sampling_outcome_variance=0`。

当前 development 数据集 SHA-256 为：

```text
8dbf474c5f4cea89146e59e406b4095e1eb8584f6eeb26124397cfc2f57baa98
```

## 8. 完整回归

```text
RAG Java tests                    381，0 failures，0 errors
RAG Execution Java tests          56，0 failures，0 errors
Java 合计                         437
完整执行语义契约                  87/87，executed=0
契约 run                          20261113040055
契约 source revision              389d0c0，dirty=false
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

隔离评测只登记并取消未确认任务，没有确认或执行真实业务动作。生产服务、生产数据库、角色和动作
allowlist 均未改变。

用户原有的 `DEPLOYMENT_TENCENT_CLOUD.md` 删除状态、`.agents/` 和
`云盘国内环境部署上线.md` 均未进入任何提交。

## 9. 提交

```text
f22137d Freeze Phase 8T compound command matrix
bb837d5 Fail closed on compound RAG mutations
ed61cbb Promote Phase 8T compound command matrix
c36931e Preserve scoped RAG command corrections
389d0c0 Refresh Phase 8T recognition baselines
```

## 10. 后续建议

阶段 8T 已闭环，不应继续围绕这 30 个 turns 堆叠规则。下一阶段如果继续提升识别率，应新建并预先
冻结独立矩阵，优先选择与复合命令不同的语义结构或产品上线后经明确授权、脱敏的真实表达。任何新的
组合执行能力仍需独立设计 ActionPlan、确认展示、幂等、补偿和权限策略，不能由识别结果自动开放。
