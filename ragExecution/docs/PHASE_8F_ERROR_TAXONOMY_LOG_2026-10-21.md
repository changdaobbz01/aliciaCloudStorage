# RAG 阶段 8F-A 错误分类基线日志｜2026-10-21

## 1. 结论

阶段 8F-A 已建立统一、可机读、可静态校验的识别错误分类基线。本节点只整理既有在线评测证据，
没有修改 RAG 识别行为、数据集、Prompt、规则、模型配置或生产环境，也没有重跑保留集。

```text
分类器提交起点         a5f73a5
在线保留集来源提交     8f47ace4776dc631c5fe35c9a874ce859b98b9e3
在线保留集 runId       20261021084230
稳定性来源提交         8a6ce3f5e91d1f273f1bd04504ff593a603eb73d
稳定性运行轮数         3
实际执行               0
生产发布               未进行
```

机器基线保存在 `test/rag-e2e/recognition-error-baseline.deepseek-online.json`，分类定义保存在
`test/rag-e2e/recognition-error-taxonomy.json`。

## 2. 分类法

分类法把问题分为十类，并为每类绑定严重度和处理策略：

| 类别 | 严重度 | 处理边界 |
| --- | --- | --- |
| availability_failure | blocker | 先恢复依赖和真实在线来源证据 |
| safety_violation | blocker | 立即停止评测并修复可执行安全边界 |
| safety_semantics | high | 修复否定、条件、引用等通用语义 |
| parameter_contract | high | 修复权威字段来源和规范化边界 |
| operation_semantics | high | 修复意图到语义操作的映射 |
| collection_semantics | medium | 统一量词、类别、后缀和名称谓词 |
| conversational_boundary | medium | 扩充互斥边界样本并统一路由优先级 |
| intent_routing | medium | 使用可复用语义特征修复普通路由 |
| sampling_outcome_variance | medium | 先测量复现率，再调整模型或权威规则 |
| arbitration_path_variance | info | 仅审计路径分布，不把等价结果视为回归 |

`classify-recognition-errors.ps1` 只读取指标中的样本键和结构化期望/实际结果，不读取数据集原句，
输出也不保存原句。分类结果用于决定优化方向，禁止把保留集措辞复制进规则或开发集。

## 3. 当前基线

在线保留集共 29 turns，完整标签契约 25/29。4 项偏差分类如下：

```text
conversational_boundary       2
collection_semantics          1
safety_semantics              1
availability_failure          0
safety_violation              0
```

其中安全语义偏差没有产生可确认或可执行计划，`unsafeExecutableCount=0`，但其严重度高于普通准确率问题，
所以后续开发样本扩充应先处理这类通用表达。三轮开发集稳定性另有 1 项
`arbitration_path_variance`：识别路径在 `deepseek` 与 `local_policy` 之间变化，意图、操作和完整契约结果
保持一致，不属于业务语义回归。

## 4. 静态门禁

`verify-static.ps1` 现在同时校验：

- 分类法 schema、版本、必需类别、类别唯一性、严重度、描述和处理策略。
- 错误基线的分类法哈希、开发集哈希、保留集哈希、来源提交和运行标识。
- 准确率错误数、稳定性波动数、分类计数与逐项记录的一致性。
- 每条记录的类别和严重度必须来自当前分类法。
- `availability_failure` 与 `safety_violation` 两个阻断类别必须为 0。

因此，后续修改分类法或数据集时，旧错误基线会明确失败，必须经过审查后显式重建。

## 5. 验证与边界

```text
PowerShell 脚本解析             11/11
开发集覆盖                     59 turns
保留集覆盖                     29 turns
在线稳定性基线                 3 轮，无语义结果漂移
错误基线                       4 errors + 1 variance
阻断级错误                     0
Compose 静态解析               通过
git diff --check               通过
```

本节点没有 Java、Android 或服务运行时业务变更，因此没有重复执行 663 项 Java 回归；阶段 8E 之前已完成的
663 项 Java 回归和 87/87 在线执行语义契约仍然适用。生产继续保持
`admin-single + RAG_ADMIN + FOLDER_CREATE`，未扩大角色或动作白名单。

## 6. 下一步

1. 进入阶段 8F-B，按类别设计新的通用开发表达，不复制或轻微改写保留集原句。
2. 优先补充安全语义的正反例，再补集合语义和会话边界；每批只解决一个主要层面。
3. 数据集变化后先重建本地与在线开发基线，运行开发集、Java 回归和 87 项契约门禁。
4. 开发节点稳定后只运行一次保留集，评估泛化变化，不按单条失败循环调参。
