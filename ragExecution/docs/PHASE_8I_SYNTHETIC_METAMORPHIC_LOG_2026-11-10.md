# RAG 阶段 8I 独立拟真表达与变形测试闭环日志｜2026-11-10

## 1. 结论

阶段 8I 已完成并闭环。项目仍处于开发阶段，暂时没有足量、经过授权且可脱敏的匿名真实表达；
本轮没有把缺少真实语料当作停止条件，而是先定义 20 项独立拟真与最小变形候选，再测量旧实现、
按结构类别修复，并在本地回退和 DeepSeek 在线两条通道通过后一次性晋升。

最终状态：

```text
正式开发集                    110 turns
待晋升候选队列                  0 turns
保留集                         29 turns
生产阶段                       admin-single（未修改）
生产唯一动作                   FOLDER_CREATE（未修改）
真实业务执行                    0
unsafeExecutableCount           0
```

本轮没有生产部署、角色扩容、动作 allowlist 扩大或确认状态机变更。

## 2. 候选设计与真实时序

候选在读取当前实现输出前先固定标签，覆盖以下边界：

```text
执行 / 咨询 / 引用边界                 4
集合 / 原子最小对照                    8
名称、扩展名、前缀谓词变形             3
空白噪声、括号命名、自然搜索、组合操作  5
总计                                  20
```

首次在线测量反映旧实现的真实结果：

```text
intent          15/20
operation       15/16
exact           15/20
unsafe           0
```

主要偏差是转述破坏性指令、类别错字、扩展名集合、名称谓词、自然目录内容和组合操作边界。标签审查
同时纠正了两处评测风险，而不是迎合实现：窄义“表格文件”不能安全映射为宽义 `DOCUMENT`，因此
保持 fail-closed；`folder_create_then_upload` 按 action bridge 契约以 `UPLOAD` 为主操作。

结构修复后，干净提交 `7168c42` 上的候选在线结果为：

```text
intent          20/20
operation       16/16
exact           20/20
unsafe           0
executed         0
```

随后 20 项候选一次性晋升，开发集由 90 项扩大到 110 项，候选队列清空。

## 3. 结构修复

修复集中在统一守卫、参数解析和权威结构路由层，没有堆叠候选整句：

- 非执行守卫补全“清空”等转述风险词，并扩展确认流程咨询表达；安全绕过检测仍保持更高优先级；
- 类别归一化支持有限的常见错字和单件量词，同时保留单个对象边界；
- 移动解析补全“放到”等自然表达，并将扩展名、名称谓词、类别集合统一映射到既有集合意图；
- 本地、手机、设备、附件等客户端输入显式阻止集合移动接管，继续保持上传语义；
- 自然目录内容问题统一落到 `LIST_CHILDREN`；
- “表格文件”作为后端无法安全表达的窄业务类别由能力边界拒绝，避免误操作其他文档；
- 组合创建并上传补全“传进/传进去”，仍沿用既有两步计划契约。

Java 正反测试同时保护批量/单件、云端移动/客户端上传、支持类别/不支持业务类别等边界。

## 4. 最终识别指标

### 4.1 本地回退

同一干净提交 `d54eaa6`：

```text
development    intent 110/110   operation 68/68   exact 110/110   unsafe 0
holdout        intent  23/29    operation 12/17   exact  22/29    unsafe 0
```

本地保留集与阶段 8H 相同，没有退化；开发集新增 20 项全部正确。

### 4.2 DeepSeek 在线

同一干净提交 `d54eaa6` 上连续三轮开发集均为：

```text
intent 110/110   operation 68/68   exact 110/110   unsafe 0
```

稳定性聚合结果：

```text
runCount                   3
intentDriftCount           0
operationDriftCount        0
exactOutcomeDriftCount     0
pathDriftCount             5
alwaysExactCorrectCount  110
fullyStableCount         105
```

5 项变化只发生在模型直接采用与本地权威接管路径之间，最终意图、操作和完整契约始终一致。

开发节点关闭后的唯一一次在线保留集为：

```text
intent 28/29   operation 16/17   exact 28/29   unsafe 0
```

唯一偏差是无上下文确认被识别为普通确认，而不是 `fallback`。该结果没有可执行计划，不构成安全违规；
错误分类基线如实记录 1 项 `intent_routing` 和 5 项 `arbitration_path_variance`。本轮没有重复运行
保留集刷分，也没有按该原句追加特例。

## 5. 回归与机器基线

```text
RAG Java tests                    343，0 failures，0 errors
最终在线执行语义契约              87/87，executed=0
契约基线                          哈希完全一致
静态脚本/JSON/数据集校验          通过
Compose 隔离和动作 allowlist      通过
git diff --check                  通过
```

四份机器基线已按 110 项开发集的新哈希重建：

```text
test/rag-e2e/recognition-baseline.local-fallback.json
test/rag-e2e/recognition-baseline.deepseek-online.json
test/rag-e2e/recognition-stability.deepseek-online.json
test/rag-e2e/recognition-error-baseline.deepseek-online.json
```

关键提交：

```text
37192ad Define phase 8I metamorphic candidates
9431361 Align phase 8I labels with capability contract
7168c42 Harden phase 8I metamorphic boundaries
d54eaa6 Promote phase 8I metamorphic candidates
3f8dc27 Refresh phase 8I recognition baselines
```

## 6. 生产边界与下一步

阶段 8I 只完成隔离环境识别优化，没有发布到生产。生产继续保持
`admin-single + RAG_ADMIN + FOLDER_CREATE`；识别准确率提升不构成扩大执行动作或用户角色的依据。

当前证据支持结束本开发节点并进入下一阶段。下一步应优先积累新的、经过授权且脱敏的真实匿名表达，
或设计不读取现有 holdout 的新候选批次；不要继续反复采样当前 29 项保留集。若决定发布本轮识别修复，
应作为独立生产变更执行完整预检、备份、部署和验收，并继续维持确认后才执行的安全边界。
