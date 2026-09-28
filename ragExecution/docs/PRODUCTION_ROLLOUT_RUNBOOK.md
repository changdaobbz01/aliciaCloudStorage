# RAG Execution 生产灰度运行手册

## 1. 当前边界

截至 2026-09-28，代码、本地隔离容器、真实 MySQL 8、Android 单动作和组合上传真机链路均已验收。生产开关仍默认关闭，本手册和脚本的存在不表示生产已经切流。

生产阶段只能按下面顺序推进：

```text
foundation -> shadow -> admin-single
```

禁止跳级，禁止在首轮灰度启用批量或组合动作，禁止自动恢复旧客户端本地写执行器。

## 2. 阶段定义

| 阶段 | 登记 | 公开确认 | Worker/Cloud 分派 | 权限与动作 |
| --- | --- | --- | --- | --- |
| `foundation` | 关闭 | 关闭 | 关闭 | 无动作 |
| `shadow` | 开启 | 关闭 | 关闭 | 只持久化计划，不执行业务 |
| `admin-single` | 开启 | 开启 | 开启 | 仅 `RAG_ADMIN`，仅一个显式单动作 |

首个 `admin-single` 动作使用 `FOLDER_CREATE`。可选单动作只包括 `NODE_RENAME`、`NODE_TRASH`、`NODE_MOVE`、`FOLDER_CREATE`、`SHARE_CREATE`；其中 `SHARE_CREATE` 仅限无密码分享，批量动作和 `UPLOAD_FILES` 不得作为首轮单动作。

## 3. 通用前置条件

- 云盘和主站仓库 tracked 状态干净，目标提交已经完成本地回归。
- 生产 `.env` 不使用示例密码，两个 HMAC 密钥彼此不同且至少 32 字符。
- `mainSite/mainSiteApi` 是唯一 Identity 所有者，Identity、CloudStorageApi 与 RAG 当前健康。
- 发布窗口内先生成并验证生产备份；备份必须包含已经存在的 `alicia_rag_execution` 数据库。
- Android Release 的云端执行开关仍保持关闭，直到 `admin-single` 服务端观察通过且另行批准客户端灰度。

## 4. 候选配置

候选生成脚本不会覆盖 `.env`：

```bash
cd ~/aliciaCloudStorage
bash deploy/scripts/prepare-rag-execution-production-env.sh foundation
```

输出只包含候选路径、备份路径和后续命令，不输出密钥。先对候选执行只读预检：

```bash
ALICIA_RAG_EXECUTION_ENV_FILE=deploy/generated/rag-execution/<candidate.env> \
  bash deploy/scripts/verify-rag-execution-production.sh foundation FOLDER_CREATE --preflight
```

安装候选前必须备份当前 `.env`：

```bash
install -m 600 .env deploy/generated/rag-execution/<stage.backup.env>
install -m 600 deploy/generated/rag-execution/<candidate.env> .env
```

## 5. 发布命令

不带 `--apply` 时只检查并打印计划：

```bash
bash deploy/scripts/update-rag-execution-production.sh \
  --stage foundation \
  --action FOLDER_CREATE
```

只有批准生产变更后才执行：

```bash
bash deploy/scripts/update-rag-execution-production.sh \
  --stage foundation \
  --action FOLDER_CREATE \
  --apply
```

脚本默认先运行生产备份，然后重建 `api`、`rag`、`rag-execution` 和 `frontend`。运行态验收失败时，默认停止 `rag-execution`，不会开启旧客户端执行器。

## 6. 阶段观察与晋级

### foundation

- `/rag-execution/api/health` 与依赖健康均为 `200/ok`。
- 数据库迁移达到 V4。
- 六个服务端执行开关全部符合 foundation 配置。
- 公网 `/rag-execution/internal/**` 和 `/internal/rag-execution/**` 均为 404。
- 容器健康，重启次数为 0；Cloud、RAG、Identity 原有业务无回归。

### shadow

- RAG 计划响应保持原有语义；登记失败不得影响响应。
- 新登记任务只处于 `PENDING_CONFIRMATION`，不会进入队列或产生 Cloud 动作回执。
- 登记成功率、重复计划幂等、过期清理和数据库增长符合预期。
- Identity 依赖健康；Cloud 动作入口仍关闭。

### admin-single

- 只有 `RAG_ADMIN` 可以确认，普通 `RAG_USER` 稳定返回 403。
- allowlist 只有一个动作，首选 `FOLDER_CREATE`。
- 每个确认任务只有一次业务变更和一个成功 Cloud 回执。
- 客户端退出、确认响应丢失和服务重启场景仍由服务端状态收敛。
- 任何越权、版本冲突、过期任务或载荷变化都必须明确拒绝。

每一阶段必须完成独立观察窗口并保存验收记录，不能在同一次发布中连续跨越三个阶段。

## 7. 统一验收

```bash
bash deploy/scripts/verify-rag-execution-production.sh <stage> FOLDER_CREATE
ALICIA_STATUS_CHECK_RAG_EXECUTION=true \
  bash deploy/scripts/collect-production-status.sh
```

验收脚本不会打印用户 Token、HMAC 密钥或动作 payload。

## 8. 回退

出现异常时先停止领取新任务：

```bash
sudo docker compose -f compose.yaml -f compose.https.yaml \
  --profile rag-execution-foundation stop rag-execution
```

随后恢复发布前 `.env`，并只以 `foundation` 配置重新启动和验证：

```bash
install -m 600 deploy/generated/rag-execution/<stage.backup.env> .env
bash deploy/scripts/update-rag-execution-production.sh --stage foundation --apply
```

`RUNNING` 或响应不明确的任务必须先查询 Cloud 动作回执；不能直接重放、伪造失败或删除审计记录。数据库只允许向前修复，不执行破坏性降级。普通云盘手工操作保持可用。

## 9. 后续门槛

只有 `admin-single` 在生产稳定观察完成后，才能讨论客户端小比例灰度。批量和组合动作必须分别验收。旧本地执行路径只能在明确兼容窗口结束、生产稳定记录完成后进入阶段 7 删除；意图识别优化在阶段 7 稳定后开始。
