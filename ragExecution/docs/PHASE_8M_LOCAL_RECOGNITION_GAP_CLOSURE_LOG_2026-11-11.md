# RAG 阶段 8M 本地识别结构缺口闭环日志｜2026-11-11

## 1. 结论

阶段 8M 已完成阶段 8L 留下的 10 项“在线正确、本地兜底失败”结构缺口修复。修改集中在权威解析层和配置化词表，没有新增原句硬编码，没有扩大执行角色、动作白名单或生产发布范围。

最终数据集状态：

```text
development                       177 turns（原 167，新增 10）
candidate                           0 turns
production-probe                   10 turns（仅保留运行时、近重复和在线波动项）
holdout                            29 turns（未运行）
生产阶段                           admin-single（未修改）
生产唯一动作                       FOLDER_CREATE（未修改）
真实业务执行                       0
```

## 2. 修复范围

本轮只处理阶段 8L 已确认的 8 个结构类别：

1. 图片类别别名：`相片`、`截图`；
2. 任意安全扩展名移动中的 `归档至` 表达；
3. 名称谓词中的简写 `含`、`带` 与前缀重命名；
4. 以类别词开头的精确文件名仍保持单节点移动；
5. “某文件夹里目前都放了什么内容”自然子项查询；
6. “文件先放一边”后的闲聊请求；
7. 带“检索”的产品改进反馈；
8. “写一句提醒/通知/消息”短文本写作请求。

对应的 7 项 candidate 和 3 项 production-probe 全部晋升到 development。以下内容明确不在本轮范围：

```text
动态目录名的生产登记/取消探针
多轮目录名称补充与取消状态机
根目录查询近重复样本
why_no_direct_write 在线普通意图路由波动样本
```

## 3. 结构化实现与反例

修改采用共享结构而不是逐句特判：

- `StorageFileCategory`、通用参数解析和实体词表共同补齐图片别名；
- 扩展名、目标目录和移动动作词表统一补齐 `归档至`；
- 共享名称谓词继续负责名称包含语义，配置规则只补齐缺失操作符；
- 自然目录内容问句必须同时具备命名目录、范围介词和明确疑问词；
- 暂停文件处理后的闲聊在模型调用前进入非执行守卫；
- 产品反馈和短文本写作只产生 `none` 动作草稿。

新增反例覆盖：

```text
相片-封面.jpg                  仍为 node_move
截图说明.txt                  仍为 node_move
音乐-片头.mp3                 仍为 node_move
按名称检索报销单              仍为 file_search
单文件重命名                  仍为 file_rename
```

完整 RAG 单元回归同时验证 449 条不支持语料不会生成可执行草稿。

## 4. 晋升前双通道证据

真实本地兜底使用无效隔离模型 key，确保没有把模型成功误记为本地能力：

```text
candidate run 20261111024832     7/7，operation 5/5，unsafe=0
probe 本轮目标                    3/3
recognitionSources                local_fallback only
```

同一修改在线复验：

```text
candidate run 20261111024858     7/7，operation 5/5，unsafe=0
recognitionSources                deepseek=6，local_fallback=1
```

探针中其余本地失败仍保持原状，没有被本轮扩大处理范围；这证明改造没有把运行时执行问题伪装成普通识别修复。

## 5. 晋升后正式基线

代码与数据提交：

```text
f8086ab Close reviewed local recognition gaps
```

干净提交上的真实本地兜底：

```text
run 20261111025045
intent / exact                    177/177
operation                         112/112
unsafe / executed                0 / 0
recognitionSources                local_fallback=177
```

同一提交、同一 development 数据集的连续三轮在线结果：

```text
run 20261111025127       177/177，operation 112/112，deepseek=113，local_fallback=64
run 20261111025656       177/177，operation 112/112，deepseek=111，local_fallback=66
run 20261111030242       177/177，operation 112/112，deepseek=112，local_fallback=65
```

稳定性聚合：

```text
runCount                           3
intentDriftCount                   0
operationDriftCount                0
exactOutcomeDriftCount             0
pathDriftCount                     3
alwaysExactCorrectCount          177
fullyStableCount                 174
unsafe / unavailable               0 / 0
```

3 项变化只发生在 DeepSeek 与本地权威接管路径之间，最终意图、操作和完整标签契约始终一致。

## 6. holdout 封存与机器基线

holdout 文件未变，本轮继续携带上一份同 lane 的封存证据，没有为了刷新 development 基线重复运行 holdout：

```text
developmentSourceRevision  f8086ab110b35101460fb0d581b521f1c8576b1d
holdoutSourceRevision      d54eaa6706aef4b36ece99e8cb837c7cfbf58c20
local fallback holdout     23/29
DeepSeek online holdout    28/29
online holdout errors       1（既有 intent_routing）
```

更新的机器基线：

```text
test/rag-e2e/recognition-baseline.local-fallback.json
test/rag-e2e/recognition-baseline.deepseek-online.json
test/rag-e2e/recognition-stability.deepseek-online.json
test/rag-e2e/recognition-error-baseline.deepseek-online.json
```

基线提交：

```text
73ef036 Refresh phase 8M recognition baselines
```

## 7. 完整回归

```text
RAG Java tests                    350，0 failures，0 errors
RAG Execution Java tests          56，0 failures，0 errors
Java 合计                         406
完整执行语义契约                  87/87，executed=0
契约基线哈希                      bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查         通过
git diff --check                  通过
```

## 8. 生产边界与下一步

阶段 8M 仅修改本地源码、识别数据和机器基线，没有执行生产部署。生产继续保持 `admin-single + RAG_ADMIN + FOLDER_CREATE`。

下一阶段不应再回头重复处理这 10 项。建议把剩余 10 turns production-probe 分成两条独立工作线：

1. 目录创建与多轮状态机：在隔离执行环境中验证登记、取消、attempts 和回执，不并入普通识别基线；
2. 普通意图在线波动：用不同措辞扩展独立测试集后再优化，不围绕单句堆规则。

holdout 仍只在明确的新模型或新路由版本验收时运行。
