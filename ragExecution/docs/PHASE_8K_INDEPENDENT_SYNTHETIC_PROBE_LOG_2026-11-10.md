# RAG 阶段 8K 独立合成表达生产探针日志｜2026-11-10

## 1. 结论

阶段 8K 已完成独立冻结矩阵、本地缺陷收敛、完整回归、生产主动探针、执行白名单缺口修复和修复后生产
验收。

```text
最终识别                    48 / 48
最终操作语义                32 / 32
unsafe                      0
真实执行                    0
允许动作任务                6 个 FOLDER_CREATE
任务最终状态                6 个 CANCELLED
步骤 attempts               0
Cloud 动作回执              0
合成名称 storage_node       0
全库非终态任务              0
生产边界                    RAG_ADMIN + FOLDER_CREATE
```

本轮输入是用户授权、预先标注的新合成表达，不包含真实用户内容，不能表述为匿名真实流量。8J candidate、
8K production-probe 和 holdout 始终分离；本轮没有运行 holdout，也没有自动改标签或晋升 development。

## 2. 冻结数据与提交

```text
数据文件       test/rag-e2e/recognition-probe.json
数据版本       rag_recognition_production_probe_8k_v1
案例数         46
turn 数        48
冻结提交       e8d569b Prepare phase 8K independent probes
语义修复       ce7306d Harden phase 8K semantic boundaries
登记防线       e2bddf0 Enforce execution allowlist at registration
```

矩阵在读取本地模型或生产输出前已经冻结。所有非 `FOLDER_CREATE` 写意图明确要求没有 execution
reference；目录创建要求登记后立即取消并校验 `attempts=0`。

## 3. 本地发现与语义修复

首次本地运行 `20261110092743` 为 40/48、unsafe=2、executed=0，暴露：

- 否定上传、否定创建和自然取消句式覆盖不足；
- 通用操作咨询被归入产品信息；
- 裸目录名没有进入多轮补槽；
- 模糊整理、暂停文件操作后转聊天等边界仍可能形成错误操作语义。

第二轮 `20261110093349` 提升为 44/48、unsafe=1、executed=0；其中一项 503 来自 Identity 单次读取超时，
另三项继续按通用语义边界修复，没有改动冻结标签。最终本地运行：

```text
run                    20261110093716
intent exact           48 / 48
operation exact        32 / 32
unsafe / executed      0 / 0
```

修复包括否定动词变体、操作前咨询、通用能力咨询、模糊整理/放置、暂停操作后聊天、自然取消以及裸目录名
提取，并增加相应单元与多轮会话测试。

## 4. 发布前回归

```text
RAG Java tests                 347 / 347
RAG Execution Java tests       56 / 56
完整执行语义契约 run           20261110093934
完整执行语义契约               87 / 87，executed=0
契约基线哈希                   bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态/Compose/密钥排除检查      全部通过
git diff --check               通过
```

用户原有 `DEPLOYMENT_TENCENT_CLOUD.md` 删除、`.agents/` 和 `云盘国内环境部署上线.md` 未被暂存或修改。

## 5. 首次生产探针与安全审计

`ce7306d` 只重建 RAG，Cloud API 和 RAG Execution 启动时间未变化。首次生产报告：

```text
run                    20261110094455
intent exact           48 / 48
operation exact        32 / 32
label exact            46 / 48
unsafe / executed      0 / 0
```

两条删除意图得到意外 execution reference：一条 `NODE_TRASH`，一条 `NODE_BATCH_TRASH`。探针立即取消，
没有确认或执行。连同 6 个允许的目录任务，交叉审计结果为：

```text
相关任务                 8
CANCELLED                8
步骤 attempts            0
事件                     每项 1:REGISTERED -> 2:CANCELLED
事件序号缺口             0
Cloud 动作回执           0
同批次 storage_node      0
非终态任务               0
```

因此该问题没有产生业务写入，但证明“配置只允许 FOLDER_CREATE”与“登记入口实际接受的动作”之间存在缺口。

## 6. 登记白名单防线

代码审查确认：执行服务原先在公开确认阶段校验 `allowedActions`，内部登记事务写库前未校验。提交
`e2bddf0` 增加服务端最终防线：

- `admin-single`（`publicConfirmEnabled=true`）在持久化前拒绝任何非 allowlist 步骤，返回 422
  `action_not_enabled`；
- `shadow` 仍保留影子登记语义，不因空执行 allowlist 而失效；
- 集成测试证明被拒计划不会写入 `rag_execution`。

本地 RAG Execution 56 项测试和冻结 48-turn 矩阵均通过后，只重建生产 `rag-execution`。Cloud API、
RAG 和前端未被连带重建。新版健康响应明确报告：

```text
stage             admin-single
allowedActions    [FOLDER_CREATE]
container health  healthy
restart count     0
```

当前标准 `verify-rag-execution-production.sh admin-single FOLDER_CREATE` 全部通过，不再需要旧版兼容校验器。

## 7. 修复后生产验收

```text
run                    20261110095532
source revision         e2bddf0
target mode             external-https
intent exact            48 / 48
operation exact         32 / 32
label exact             48 / 48
recognition sources     deepseek=30, local_fallback=18
unsafe / executed       0 / 0
Identity session        已注销
```

数据库最终证据：

```text
新任务                    6，全部 FOLDER_CREATE
最终状态                  6 个 CANCELLED
步骤 attempts             0
事件序号                  每项 1..2，连续无缺口
近期 Cloud 回执           0
取消/过期任务误回执       0
同批次 storage_node       0
全库非终态任务            0
卡住或重试中步骤          0
```

RAG 日志中两条 `UnprocessableContent` 正是非白名单删除计划被执行服务以 422 拒绝的预期记录；执行服务没有
新增异常日志。公开 Android CURRENT 仍为 `0.1.14`，本轮未改动应用包。

## 8. 后续决策

阶段 8K 已完成，可以进入一次独立的样本晋升评审，但本轮结果本身不自动改变数据集：

1. 继续保持 `RAG_ADMIN + FOLDER_CREATE`，不扩大角色、批量、组合或其他动作。
2. 保留首次生产 46/48 和修复后 48/48 两份证据，不删除失败历史。
3. 8J candidate 与 8K production-probe 分开评估重复度、覆盖价值和标签稳定性后，再决定是否晋升。
4. holdout 继续封存，只有明确的新模型或新路由版本验收时才运行。
5. 后续新表达继续使用独立、冻结、授权合成矩阵，不把合成样本描述为真实流量。
