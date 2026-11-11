# RAG 阶段 8L 样本晋升独立评审闭环日志｜2026-11-11

## 1. 结论

阶段 8L 已完成 8J candidate 与 8K production-probe 的独立样本晋升评审，并按双通道门禁选择性晋升。
本轮没有把“生产探针通过”等同于“适合进入长期开发基线”，也没有为了全量晋升而新增原句特例。

最终数据集状态：

```text
正式 development                 167 turns（原 110，净增 57）
8J candidate                       7 turns
8K production-probe               13 turns
holdout                            29 turns（未运行）
生产阶段                           admin-single（未修改）
生产唯一动作                       FOLDER_CREATE（未修改）
真实业务执行                       0
```

晋升组成：

```text
8J 晋升                         22
8K 晋升                         35
总计                            57
```

## 2. 评审方法

评审在修改 development 前先固定以下门槛：

1. 标签仍描述产品期望，不按本轮模型输出反向改标签。
2. 检查 development、candidate、production-probe 与 holdout 的 case id、单轮原句和标签冲突。
3. 使用归一化字符二元组检查近重复；高相似样本必须证明新增边界价值。
4. 运行时名称、execution reference、登记/取消和多轮状态机只作为生产探针，不直接复制进识别开发集。
5. 晋升样本必须同时通过真实本地回退和 DeepSeek 在线通道。
6. 在线 development 必须在同一干净提交连续三轮无意图、操作或完整契约结果漂移。
7. holdout 继续封存，不能为了重建 development 基线而被迫重跑。

审计未发现 8J/8K 与 development 的精确归一化重复或高相似标签冲突。两项被明确判定为不应晋升：

- `probe.8k.search.root_folders` 与既有根目录文件夹查询的二元组相似度为 0.61，标签相同且没有足够新增边界；
- 8J/8K 英文目录创建形式在去除动态名称后完全相同，并且都属于运行时执行探针。

## 3. 首轮选择与真实失败证据

当前干净提交上的独立复验先得到：

```text
8J candidate run 20261111014544       32/32，operation 23/23，unsafe=0
8K probe run 20261111014655            47/48，operation 32/32，unsafe=0
```

8K 唯一偏差是：

```text
probe.8k.product.why_no_direct_write
expected  assistant_product_reason
actual    assistant_capability_examples
path      deepseek
```

同一句在 8K 最终生产报告中曾正确命中，证明它存在在线普通意图路由波动。该样本继续留在 probe，未通过
重跑刷回 48/48，也没有迎合单次输出改标签。

第一版清单曾将 67 项识别表达并入 development。随后用故意无效的隔离 DeepSeek key 验证真实本地回退，
run `20261111020219` 得到：

```text
intent / exact                    167/177
operation                         105/112
unsafe                            0
recognitionSources                local_fallback=177
```

这 10 项没有满足双通道门槛，因此立即退出 development，而不是增加特殊规则：

```text
8J candidate 退回 7 项
  collection.photo_synonym
  collection.extension_move
  collection.rename_prefix
  atomic.exact_category_filename
  search.natural_children
  conversation.casual
  product.feedback

8K probe 退回 3 项
  collection.screenshots
  collection.rename_prefix
  writing.team_reminder
```

8J 的 3 项动态目录执行探针没有进入 development；相同能力已由 8K 更完整的登记/取消矩阵覆盖，因此从
candidate 退役。它们仍可从提交历史和 8J 正式日志追溯。8K 保留的 13 turns 还包括 8 个运行时状态机
turns、1 个近重复 turn 和 1 个在线路由波动 turn。

## 4. 最终双通道证据

### 4.1 本地回退

干净提交 `b8a4111` 上的最终本地回退 run `20261111020452`：

```text
intent / exact                    167/167
operation                         105/105
unsafe / executed                0 / 0
recognitionSources                local_fallback=167
```

### 4.2 DeepSeek 在线

补充额度前的 run `20261111020553` 在 10 次模型成功后开始收到 HTTP 402，产生 102 个
`local_unavailable`。虽然本地权威层仍保持 167/167，该报告按门禁作废，未进入基线。

补充额度后，同一干净提交和同一 development 数据集连续三轮为：

```text
run 20261111021800       167/167，operation 105/105，unavailable=0
run 20261111022301       167/167，operation 105/105，unavailable=0
run 20261111022752       167/167，operation 105/105，unavailable=0
```

稳定性聚合：

```text
runCount                         3
intentDriftCount                 0
operationDriftCount              0
exactOutcomeDriftCount           0
pathDriftCount                   5
alwaysExactCorrectCount        167
fullyStableCount               162
unsafe / unavailable             0 / 0
```

5 项变化只发生在 `deepseek` 与本地权威接管路径之间，最终意图、操作和完整标签契约始终一致。

## 5. holdout 封存与基线溯源

旧基线工具要求 development 与 holdout 来自同一提交，会迫使每次开发集晋升都重复运行 holdout。提交
`1166fc6` 将来源拆为：

```text
developmentSourceRevision
holdoutSourceRevision
```

携带 holdout 必须显式指定同 lane 的上一份基线；工具继续校验 holdout 哈希、版本、覆盖、来源类型和
安全状态。holdout 文件一旦变化就会拒绝携带。

本轮四份机器基线的来源为：

```text
development       b8a41119b6e4d20bb8723d41804b8f28c9858aee
holdout           d54eaa6706aef4b36ece99e8cb837c7cfbf58c20
holdout hash      eebeb9d595799ee049fd5d349d36801b13f59fadd1f1f70a1e8e9aef8639abb7
```

因此本轮没有运行 holdout。保留的历史结果仍为：

```text
local fallback holdout           22/29
DeepSeek online holdout          28/29
```

更新文件：

```text
test/rag-e2e/recognition-baseline.local-fallback.json
test/rag-e2e/recognition-baseline.deepseek-online.json
test/rag-e2e/recognition-stability.deepseek-online.json
test/rag-e2e/recognition-error-baseline.deepseek-online.json
```

## 6. 回归结果

```text
RAG Java tests                    347，0 failures，0 errors
RAG Execution Java tests          56，0 failures，0 errors
Java 合计                         403
完整执行语义契约                  87/87，executed=0
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

关键提交：

```text
1166fc6 Decouple sealed holdout baseline provenance
8fea5e1 Promote reviewed phase 8J and 8K recognition samples
b8a4111 Defer samples that miss the local fallback gate
c437848 Refresh reviewed recognition baselines
```

## 7. 生产边界与下一步

阶段 8L 是数据集治理和隔离回归，没有生产部署。生产继续保持
`admin-single + RAG_ADMIN + FOLDER_CREATE`，未扩大角色、动作白名单、批量或组合执行能力。

下一轮识别优化应只处理当前 7 项 candidate 和 13 turns production-probe 中明确可泛化的错误类别，先新增
不同措辞的结构性反例，再修改通用路由或解析层。不要把运行时执行探针直接并入 development，也不要按
当前原句堆叠规则。holdout 只有在明确的新模型或新路由版本验收时才允许重新运行。
