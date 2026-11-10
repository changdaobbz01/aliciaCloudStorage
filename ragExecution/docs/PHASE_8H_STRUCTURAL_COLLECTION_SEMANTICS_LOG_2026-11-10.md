# RAG 阶段 8H 结构化集合语义闭环日志｜2026-11-10

## 1. 结论

阶段 8H 已完成并闭环。本轮只处理阶段 8G 错误分类基线中剩余的集合分类移动泛化问题，先使用
独立候选队列验证通用规则，再将候选晋升到正式开发集；保留集没有参与逐句调参，只在开发节点
关闭后按本地回退和 DeepSeek 在线两条通道各运行一次。

最终状态：

```text
正式开发集                    90 turns
待晋升候选队列                 0 turns
保留集                        29 turns
生产阶段                      admin-single（未修改）
生产唯一动作                  FOLDER_CREATE（未修改）
真实业务执行                   0
unsafeExecutableCount          0
```

本轮没有生产部署、角色扩容或动作 allowlist 扩大，也没有改变确认状态机。

## 2. 候选与真实时序

独立候选队列包含 8 项不同于保留集原句的表达：

1. 5 项省略“全部、统一”等显式集合量词的文件类别移动；
2. 3 项原子移动反例，分别覆盖显式单个、明确命名文件夹和精确文件名。

候选建立后，旧实现的首次在线测量为：

```text
intent/exact       3/8
operation          8/8
unsafe             0
```

5 项集合正例都被识别为 `node_move`，3 项原子反例保持正确。该结果证明问题位于集合选择器结构，
而不是 MOVE 操作本身。本轮没有读取或复制保留集原句来补规则。

结构修复后，候选在两条通道分别达到：

```text
local_fallback     8/8 exact，unsafe 0
DeepSeek online    8/8 exact，unsafe 0
```

随后 8 项候选一次性晋升，开发集由 82 项扩大到 90 项，候选队列清空。

## 3. 结构修复

修复集中在统一参数解析和权威路由层，没有向提示词或路由表堆叠整句特例：

- `StorageFileCategory` 增加类别选择器归一化，统一处理“视频文件、文档资料、音频类型的文件”等形态；
- `OperationArgumentResolver` 将解析出的标准类别保存在来源选择器中，并区分显式单个与集合语义；
- `IntentRecognitionService` 仅在 MOVE 来源同时满足“通用选择器、标准类别、非显式单个”时，将
  `node_move` 权威提升为 `collection_move_by_category`；
- “某个视频”、明确命名的类别同名文件夹和精确文件名继续走原子移动；
- 补齐“归档到/归档至”的移动参数分隔，并在语义帧复制时完整保留类别元数据。

Java 回归同时覆盖 5 项集合正例和 3 项原子反例，防止以后为提高集合召回率再次吞掉原子边界。

## 4. 最终识别指标

### 4.1 本地回退

```text
development    intent 90/90    operation 52/52    exact 90/90    unsafe 0
holdout        intent 23/29    operation 12/17    exact 22/29    unsafe 0
```

相较阶段 8G，本地保留集完整契约由 21/29 提升到 22/29；新增的 8 项开发样本全部正确。

### 4.2 DeepSeek 在线

同一干净提交 `8aae546` 上连续三轮开发集均为：

```text
intent 90/90    operation 52/52    exact 90/90    unsafe 0
```

稳定性聚合结果：

```text
intentDriftCount         0
operationDriftCount      0
exactOutcomeDriftCount   0
pathDriftCount           2
alwaysExactCorrectCount  90
fullyStableCount         88
```

两项变化只发生在 `deepseek` 与本地权威路径之间，意图、操作和完整契约结果始终一致。

开发节点关闭后的唯一一次在线保留集结果为：

```text
intent 29/29    operation 17/17    exact 29/29    unsafe 0
```

阶段 8G 唯一剩余的 `collection_semantics` 偏差已消除，错误分类基线中的准确率错误、可用性故障、
安全语义和安全违规均为 0；只保留 2 项信息级仲裁路径波动。

## 5. 回归与机器基线

```text
RAG Java tests                    339，0 failures，0 errors
最终在线执行语义契约              87/87，executed=0
契约基线                          哈希完全一致
静态脚本/JSON/数据集校验          通过
Compose 隔离和动作 allowlist      通过
git diff --check                  通过
```

四份机器基线已按 90 项开发集的新哈希重建：

```text
test/rag-e2e/recognition-baseline.local-fallback.json
test/rag-e2e/recognition-baseline.deepseek-online.json
test/rag-e2e/recognition-stability.deepseek-online.json
test/rag-e2e/recognition-error-baseline.deepseek-online.json
```

关键提交：

```text
b670f41 Define phase 8H collection candidates
4903cbd Preserve implicit category collection moves
8aae546 Promote phase 8H recognition candidates
bdc0fe8 Refresh phase 8H recognition baselines
```

## 6. 生产边界与下一步

阶段 8H 只完成隔离环境识别优化，没有发布到生产。生产继续保持
`admin-single + RAG_ADMIN + FOLDER_CREATE`；识别准确率提升不构成扩大执行动作或用户角色的依据。

当前阶段 8 的既定开发集和保留集均已达到在线完整契约全通过。下一步适合先冻结本轮识别基线，
把新的真实匿名表达作为下一批独立候选积累；在出现足够的新错误类别前，不继续对现有 29 项保留集
重复采样或追加规则。若后续决定发布识别修复，应作为独立生产变更执行完整预检、备份、部署与验收。
