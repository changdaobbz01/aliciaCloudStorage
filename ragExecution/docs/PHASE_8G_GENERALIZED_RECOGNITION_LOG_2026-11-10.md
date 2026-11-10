# RAG 阶段 8G 泛化识别与安全闭环日志｜2026-11-10

## 1. 结论

阶段 8G 已完成并闭环。本轮继续使用独立候选队列扩充通用表达，在本地兜底与 DeepSeek 在线链路稳定后，才将 10 项候选晋升到正式开发集；保留集只在开发节点完成后运行，没有用于逐句调参。

最终状态：

```text
正式开发集                    82 turns
待晋升候选队列                 0 turns
保留集                        29 turns
生产阶段                      admin-single（未修改）
生产唯一动作                  FOLDER_CREATE（未修改）
真实业务执行                   0
unsafeExecutableCount          0
```

本轮没有发布生产版本，没有扩大账号、角色或动作 allowlist，也没有改变确认状态机。公开 Android 版本和生产运行态均未由本轮修改。

## 2. 本轮范围

新增的 10 项候选覆盖四类通用表达：

1. 3 项仅按扩展名或后缀批量删除的破坏性请求；
2. 2 项助手身份和职责边界咨询；
3. 2 项操作流程或确认能力咨询；
4. 3 项图片、音频和视频分类移动表达。

候选通过本地和在线评测后一次性晋升，开发集从 72 项扩大到 82 项。候选队列随后清空，保留集仍保持独立。

## 3. 按真实时序发现并修复的问题

### 3.1 集合分类与能力边界泛化

- 扩充“收拢、归拢、归入”等集合移动动词，并补充“影片”等类别别名；
- 集合预览与识别统一通过 `StorageFileCategory` 做类别归一化，避免两套映射继续分叉；
- 增加操作流程问句和助手职责问句的非执行路由，咨询不会生成动作草稿；
- 扩展破坏性能力边界对扩展名、后缀和格式选择器的覆盖。

### 3.2 在线测量暴露的删除安全缺口

在中间版本的首次在线保留集测量中，`holdout.collection.delete_extension` 曾被模型识别为可执行删除，并生成扩展名选择器。该结果使 `unsafeExecutableCount` 出现 1，节点随即停止，未用于基线，也没有确认或执行任务。

修复没有继续堆叠原句规则，而是在后端统一语义契约层增加第二道 fail-closed 边界：

- `DELETE` 同时包含扩展名选择器、但没有同扩展名的精确单文件目标时，强制降为 `fallback / UNKNOWN`；
- 不生成可执行 `ActionDraft`、`ActionPlan` 或任务确认引用；
- 精确文件名删除仍可用；
- 按扩展名移动仍可用。

最终本地与在线保留集均确认该样本不可执行，`unsafeExecutableCount=0`。

### 3.3 在线采样波动的确定性边界

修复删除安全缺口后的在线开发集曾对一个超出产品能力的开放数学证明请求产生采样波动。为避免靠重复采样获得绿色结果，新增通用的“开放数学命题证明”非执行边界，并用不同表达做回归；同时保留对同名文件的正常搜索能力。

该边界只处理明确要求系统完成开放命题证明的请求，不以关键词粗暴拦截文件搜索。

## 4. 最终评测结果

### 4.1 本地兜底

```text
development    intent 82/82    operation 44/44    exact 82/82    unsafe 0
holdout        intent 22/29    operation 12/17    exact 21/29    unsafe 0
```

本地保留集完整契约由历史 19/29 提升到 21/29。该变化来自通用规则和结构边界，没有复制保留集原句。

### 4.2 DeepSeek 在线

同一干净源码提交 `8990bb7` 上，连续三轮正式开发集均为：

```text
intent 82/82    operation 44/44    exact 82/82    unsafe 0
```

稳定性聚合结果：

```text
intentDriftCount         0
operationDriftCount      0
exactOutcomeDriftCount   0
pathDriftCount           1
alwaysExactCorrectCount  82
fullyStableCount         81
```

唯一漂移只发生在 `deepseek` 与本地权威策略路径之间，意图、操作和完整契约结果始终一致。

最终在线保留集：

```text
intent 28/29    operation 17/17    exact 28/29    unsafe 0
```

唯一剩余偏差是集合分类移动被识别为单节点移动，归类为 `collection_semantics`。它是准确率误分，不是越权执行。错误分类基线中可用性故障和安全违规均为 0。

## 5. 回归与机器基线

```text
RAG Java tests                    337，0 failures，0 errors
最终在线执行语义契约              87/87，executed=0
契约基线                          哈希完全一致
静态脚本/JSON/数据集校验          通过
Compose 隔离和动作 allowlist      通过
git diff --check                  通过
```

四份机器基线已按新数据集哈希重建：

```text
test/rag-e2e/recognition-baseline.local-fallback.json
test/rag-e2e/recognition-baseline.deepseek-online.json
test/rag-e2e/recognition-stability.deepseek-online.json
test/rag-e2e/recognition-error-baseline.deepseek-online.json
```

## 6. 生产与运维边界

本轮只完成本地隔离环境和在线模型链路验证，没有拉取或部署生产。生产继续保持 `admin-single + RAG_ADMIN + FOLDER_CREATE`，不得因为识别率改善而扩大执行范围。

最终只读复核中，生产内部 stage、Compose、liveness 和依赖检查均通过，RAG Execution 容器当前为 `healthy`。容器累计 `RestartCount=30`，日志将这些重启定位到 2026-11-04 08:50 UTC 左右的 `db` 主机名临时解析失败；最后一次成功启动为 2026-11-04 08:57 UTC，此后持续运行，`OOMKilled=false`，最近 24 小时没有异常日志。该历史计数不是阶段 8G 引入的持续故障，但在下一次授权重建并重新观察前，标准生产验收不能声称“重启次数为 0”，也不得为制造绿色结果单独重建容器。

检查中确认生产 HTTPS 证书已于 2026-11-05 过期。证书更新属于独立生产变更，需要明确选择新的腾讯云证书，或授权使用 Let's Encrypt/Certbot 并接受其服务条款；在获得该选择前不得自行签发、替换或重启生产入口。

## 7. 下一阶段建议

阶段 8G 已具备进入下一开发节点的条件。下一批仍应通过独立候选队列处理剩余的集合语义泛化，不得复制保留集原句，不得反复运行保留集刷分，也不得把识别优化与生产权限扩容混在同一节点。
