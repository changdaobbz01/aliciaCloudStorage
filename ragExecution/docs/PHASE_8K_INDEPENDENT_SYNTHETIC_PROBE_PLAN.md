# RAG 阶段 8K 独立合成表达探针计划

## 1. 目标

阶段 8K 使用与 8J 完全分离的新表达矩阵，验证生产识别、安全边界和任务登记/取消链路。用户已于
2026-11-10 授权本批合成模拟测试。样本不含真实用户内容，不能表述为匿名真实流量。

## 2. 数据与时序

```text
数据文件       test/rag-e2e/recognition-probe.json
数据版本       rag_recognition_production_probe_8k_v1
评测入口       -Suite recognition-probe
案例数         46
turn 数        48
标签策略       rag_recognition_labels_v1
```

标签和表达在读取本地模型或生产输出前冻结并提交。8J 的 32 条 candidate 不参与本轮运行；holdout 也不
运行。8K 结果不会自动改写标签或直接晋升 development。

## 3. 覆盖矩阵

```text
安全否定、引用、假设与说明       8
能力咨询                         3
集合动作                         8
原子动作                         7
搜索                             4
目录创建                         4
多轮目录补槽与取消               4 turns
组合操作                         2
模糊与冲突边界                   4
对话、产品与写作                 4
总计                             48 turns
```

## 4. 安全门

- 生产继续保持 `RAG_ADMIN + FOLDER_CREATE`。
- 除 `FOLDER_CREATE` 外的所有写意图都必须没有 execution reference。
- 应登记的目录创建必须返回 execution reference，随后立即取消并复核 `attempts=0`。
- 禁止确认或执行任务；Cloud 回执和同名 storage node 必须为 0。
- 外部目标必须全部为 HTTPS；临时 Identity 会话必须注销。
- 任一 unsafe、取消失败、引用缺失、基础设施异常或残留任务都会阻断本阶段。

## 5. 执行顺序

1. 提交冻结的数据、通道和计划。
2. 运行静态校验。
3. 启动本地隔离栈，执行在线 canary 和 48-turn 测量。
4. 若有偏差，只按通用语义类别修复，不改变已冻结标签。
5. 通过 Java 全量测试与 87 项执行契约。
6. 复核生产边界和健康后执行一次生产 8K 探针。
7. 交叉审计任务、步骤、事件、回执和存储节点，再决定是否将 8J/8K 样本晋升开发集。
