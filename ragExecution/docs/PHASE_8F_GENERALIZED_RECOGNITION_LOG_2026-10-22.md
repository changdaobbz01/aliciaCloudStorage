# RAG 阶段 8F-B/8F-C 泛化识别优化日志｜2026-10-22

## 1. 结论

阶段 8F 的第一轮泛化识别优化已经闭环。开发样本先进入独立候选队列，在本地兜底和 DeepSeek 在线链路均稳定后才晋升到正式开发集；保留集没有用于逐句调参。

最终状态：

```text
正式开发集                    72 turns
待晋升候选队列                 0 turns
保留集                        29 turns
生产配置与动作白名单           未修改
真实业务执行                   0
unsafeExecutableCount          0
```

本轮没有发布生产版本，没有扩大账号、角色或动作范围，也没有改变 `admin-single + RAG_ADMIN + FOLDER_CREATE` 的生产边界。

## 2. 评测治理

为避免在历史开发集和保留集之间反复调参，新增 `recognition-candidate.json` 作为短生命周期候选队列：

1. 只放入与历史保留集措辞不同的通用表达。
2. 候选先分别通过本地兜底与真实在线模型评测。
3. Java 回归和 87 项执行语义契约保持不变后，候选才一次性晋升到开发集。
4. 晋升后候选队列允许为空；空队列不能生成零样本报告。
5. 保留集只在一个开发节点完成后运行，不根据单条失败继续修改规则。

阶段 8F-B 首批 10 项覆盖安全语义、集合移动、会话角色、能力咨询和产品模块表达。阶段 8F-C 另外加入 3 项未映射破坏性选择器安全样本。

## 3. 通用修复

### 3.1 非执行语言边界

- 扩充否定移动表达，例如“移去、搬去、挪去”。
- 引用他人文件操作并询问风险时只做安全说明。
- 询问确认流程时只返回能力说明，不生成操作计划。
- “处理一下、处理掉”等未指明动作的表达保持 `UNKNOWN` 并要求澄清。
- 规则要求完整语义组合，避免文件名中的“说明”“确认版”误伤正常重命名。

### 3.2 本地兜底泛化

- 扩充“收进、收纳进、集中到”等集合移动表达和目标目录抽取。
- 补充会话角色、同名文件处理方案、产品模块组成等通用咨询路由。
- 为产品模块规则增加问句结构和文件操作排除条件，避免拦截同名文件删除。

### 3.3 未映射破坏性选择器

后端没有“按扩展名批量删除”的动作契约。此类请求现在由配置化能力边界前置接管：

- 返回 `fallback / UNKNOWN`；
- 不生成 `ActionDraft`、`ActionPlan` 或执行引用；
- 引导用户先按扩展名搜索并检查候选，再使用受支持的删除流程；
- 精确文件名删除和按扩展名移动仍保持可用。

## 4. 最终结果

### 本地兜底

```text
development    intent 72/72    operation 38/38    exact 72/72    unsafe 0
holdout        intent 20/29    operation 12/17    exact 19/29    unsafe 0
```

本地保留集保持历史 19/29 水平，没有为了提高数字复制保留集表达。

### DeepSeek 在线

同一干净源码提交 `1b2c2fe` 上，连续三轮正式开发集结果均为：

```text
intent 72/72    operation 38/38    exact 72/72    unsafe 0
```

稳定性聚合结果：

```text
intentDriftCount         0
operationDriftCount      0
exactOutcomeDriftCount   0
pathDriftCount           2
alwaysExactCorrectCount  72
```

两项路径波动只发生在 `deepseek` 与本地权威策略之间，最终意图、操作和完整契约结果一致。

最终在线保留集：

```text
intent 26/29    operation 16/17    exact 25/29    unsafe 0
```

剩余 4 项偏差分类为：会话边界 2、集合语义 1、安全语义 1；可用性故障和安全违规均为 0。安全语义项不再产生可执行计划，但仍保留 `DELETE` 与期望 `UNKNOWN` 的语义差异，留给后续独立泛化批次处理。

## 5. 回归证据

```text
RAG Java tests                    335，0 failures，0 errors
最终在线执行语义契约              87/87，executed=0
契约基线                          完全一致
静态脚本/JSON/数据集校验          通过
Compose 隔离和动作 allowlist      通过
git diff --check                  通过
```

四份机器基线已经按新数据集哈希重建：

```text
test/rag-e2e/recognition-baseline.local-fallback.json
test/rag-e2e/recognition-baseline.deepseek-online.json
test/rag-e2e/recognition-stability.deepseek-online.json
test/rag-e2e/recognition-error-baseline.deepseek-online.json
```

## 6. 后续边界

下一批识别优化应继续从剩余错误类别设计新的候选表达，优先处理安全语义，再处理集合语义和会话边界。不得复制保留集原句，不得以准确率优化为理由扩大生产动作白名单或绕过确认状态机。
