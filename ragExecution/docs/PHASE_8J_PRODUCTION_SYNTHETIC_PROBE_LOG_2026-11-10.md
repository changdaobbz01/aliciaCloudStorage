# RAG 阶段 8J 生产合成表达主动探针日志｜2026-11-10

## 1. 结论

阶段 8J 已完成授权合成表达的首次生产测量、两类缺陷修复、修复后生产复测和无写入交叉审计。

```text
最终识别                    32 / 32
最终操作语义                23 / 23
unsafe                      0
真实执行                    0
Cloud 动作回执              0
合成名称 storage_node       0
生产边界                    RAG_ADMIN + FOLDER_CREATE
```

本轮输入全部为用户授权、预先标注的合成表达，不包含真实用户内容，因此不能称为匿名真实流量。32 条样本
继续留在 candidate 队列，不自动晋升开发集；本轮没有重复运行 holdout。

## 2. 发布前基线与安全措施

发布前服务器追踪文件干净，用户原有未追踪备份和工具文件保持不动。生产完整备份已生成并验证：

```text
deploy/generated/production-backups/20261110083708
```

备份包含 Cloud、Identity 和 RAG Execution 数据库及敏感配置。生产始终保持：

```text
ALICIA_RAG_EXECUTION_ADMIN_ONLY=true
ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=FOLDER_CREATE
registration / public confirm / worker / cloud dispatch=true
```

第一轮发布使用既有 RAG 更新脚本。Compose 依赖行为额外重建了未改源码的 Cloud API；API 随后健康且
重启计数为 0。后续两次 RAG 修复发布改用 `up -d --build --no-deps rag`，Cloud API、前端和
RAG Execution 均未被连带重建。

## 3. 首轮生产测量

冻结标签后的 32 条候选集由提交 `b05b0d6` 引入。首次生产报告：

```text
run                    20261110084639
intent exact           26 / 32
operation exact        23 / 23
unsafe                 1
executed               0
recognition sources    deepseek=23, local_fallback=9
target mode            external-https
```

六项偏差为：

1. 两条操作确认咨询被路由为产品信息。
2. 引用操作的润色请求被路由为普通确认回复。
3. 带“暂时别”的否定移动被识别为 `MOVE`，构成 unsafe；生产动作白名单拒绝登记，未产生任务引用，
   没有执行。
4. 假设删除问题退化为 fallback。
5. “散落的音乐统一放到……”被错误识别为原子移动。

安全门在写出报告后使评测失败关闭；没有通过改标签或重复运行掩盖结果。

## 4. 识别边界修复

提交 `8151f78` 完成以下修复：

- 否定操作允许“暂时、目前、现在、这次”等时间副词。
- 支持动词位于引用命令之前的润色/改写表达。
- 将操作确认咨询与假设操作问题明确归入非执行说明。
- 将“散落的/散落着的”和尾部“统一”纳入集合量词解析。
- 增加对应单元测试和识别服务回归样本。

本地证据：

```text
候选集 run             20261110085305
候选集                 32 / 32，unsafe=0，executed=0
RAG Java tests         344 / 344
完整执行语义契约 run   20261110085558
完整执行语义契约       87 / 87，executed=0
契约基线哈希           bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
```

## 5. 生产复测发现不确定登记缺陷

提交 `8151f78` 发布后，生产识别报告 `20261110090120` 达到 32/32、unsafe=0、executed=0。
其中两个目录任务正常返回引用并立即取消；中文调用式表达的 RAG 响应没有引用，日志记录：

```text
responseId  7aef01ce-6b5d-4270-bcda-e30597201869
category    RestClientException
```

数据库交叉审计证明 POST 实际已持久化任务：

```text
executionId  16e58908-c439-468e-9244-665e87e03d99
status       PENDING_CONFIRMATION
version      0
attempts     0
events       1:EXECUTION_REGISTERED
```

该任务从未确认，因此不会执行。发现后立即通过公开 API 取消并复核为 `CANCELLED`、version=1、
attempts=0、事件 `REGISTERED -> CANCELLED`，临时 Identity 会话正常注销。

根因是 HTTP 客户端只把 `ResourceAccessException` 归类为不确定结果；当 POST 已落盘但成功响应无法读取
并表现为通用 `RestClientException` 时，没有进入已有的 `planId + planHash` 只读恢复流程。

## 6. 不确定结果恢复加固

提交 `656ad0e` 完成：

- 明确 4xx 为确定性客户端错误，不进入恢复轮询。
- 其余 `RestClientException` 全部视为 POST 结果不确定，进入有界只读恢复。
- 增加“200 响应不可解析”和“400 不应恢复”两项 HTTP 客户端测试。
- 三条目录创建候选均要求 `executionReference=true`，防止探针再次把引用缺失计为成功。

修复后本地证据：

```text
HTTP/恢复针对性测试    8 / 8
RAG Java tests         346 / 346
候选集 run             20261110090745
候选集                 32 / 32
操作语义               23 / 23
三项目录任务           均返回引用并取消，attempts=0
unsafe / executed      0 / 0
```

## 7. 最终生产验收

`656ad0e` 只重建 RAG。发布后 RAG、Identity 和 Storage 依赖均为 `ok`，RAG 容器重启计数为 0，
近期无新增异常日志。使用首轮同类中文句式执行一个唯一名称的登记/取消探针：

```text
responseId   7109f11f-ec94-4ada-99af-8f6cc2bafe53
executionId  aaedeb0c-d69c-4ddc-ac6d-f693d3a7ed7f
status       CANCELLED
version      1
attempts     0
events       1:EXECUTION_REGISTERED -> 2:EXECUTION_CANCELLED
receipts     0
storage node 0
```

连同本轮另外三个生产任务的最终数据库审计：

```text
相关任务                   4
CANCELLED                  4
步骤 attempts 总和         0
事件序号缺口               0
Cloud 动作回执             0
同名 storage_node          0
```

RAG Execution 仍运行发布前镜像，历史重启计数为 30，启动时间未变化。当前源码验证器新增了
`allowedActions` 健康字段断言，而该旧镜像尚不返回该字段；因此本轮使用发布前兼容验证器只读复核，
stage、FOLDER_CREATE、依赖、公开健康、内部路由封闭和容器健康全部通过。没有为了验证脚本显示绿色而
重建执行服务。

## 8. 后续决策

阶段 8J 当前适合继续收集下一批授权、脱敏的合成表达，但不据此宣称已经代表真实用户分布。下一批应：

1. 使用新的唯一表达矩阵，不重复刷当前 32 条。
2. 继续冻结标签后再读取生产输出。
3. 对任何应登记的允许动作强制要求 execution reference，并在取消后交叉审计 attempts、事件、回执和节点。
4. 保持 `RAG_ADMIN + FOLDER_CREATE`，不扩大角色和动作白名单。
5. 等独立批次稳定后，再一次性评审 candidate 是否并入 development；holdout 仍不提前重复运行。
