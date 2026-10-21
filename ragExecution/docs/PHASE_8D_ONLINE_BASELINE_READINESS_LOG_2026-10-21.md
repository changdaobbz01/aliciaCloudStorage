# RAG 阶段 8D 在线基线就绪日志｜2026-10-21

> 后续 DeepSeek 账号可用性已恢复，在线基线已完成。最终结果见
> [阶段 8D 在线基线日志](PHASE_8D_ONLINE_BASELINE_LOG_2026-10-21.md)。本文件保留首次 HTTP 402
> 失败关闭证据和恢复入口。

## 1. 结论

阶段 8D 已完成 DeepSeek 在线识别基线的失败关闭门禁和可审计来源统计，但**尚未形成在线基线**。
使用无执行能力的安全 canary 调用真实模型时，DeepSeek 返回 HTTP 402；流程已在数据集运行和报告
写入之前停止，没有用本地兜底结果冒充在线模型准确率，也没有继续消耗保留集。

```text
门禁源码提交         581be2b051e430b4311c256f1d8a0e1ece3e8c6a
在线 canary          未通过
脱敏诊断             deepseek_http_402
在线开发集           未运行
在线保留集           未运行
在线基线文件         未写入
生产发布             未进行
```

HTTP 402 表明当前 DeepSeek 账号计费或余额状态不可用。密钥值未打印、未写入报告，也未提交到 Git。
本阶段不通过绕过门禁、降低来源要求或把 `local_fallback` 改名为 `deepseek` 来继续。

## 2. 新增门禁

`run-evaluation.ps1 -RequireOnlineRecognition` 在读取正式数据集前发送固定安全 canary
“谢谢你的帮助”，并强制要求：

- 直接返回 `provider=deepseek` 且模型名非空，或存在仅在模型成功后生成的 `model_succeeded:local_policy` 审计标记。
- 意图为 `assistant_social`。
- 不产生动作草稿或执行任务引用。
- 任何 HTTP、网络、超时、JSON 或响应结构异常都立即失败。
- 失败时只输出脱敏诊断分类，不输出密钥、上游响应正文或认证材料。
- canary 失败时不生成数据集报告，因此旧报告数量保持不变。

识别指标使用六条互斥路径：

```text
deepseek           在线模型结果直接保留
local_guard        模型调用前由本地安全边界拦截
local_structure    无需调用模型的确定性结构语义
local_policy       模型成功后由本地权威规则接管
local_unavailable  模型未配置、调用失败或响应不可用
unclassified       无法归类，视为门禁失败
```

`deepseek-online` 基线允许前四条可审计路径，拒绝 `local_unavailable` 和 `unclassified`，并要求至少存在
一次真实在线成功证据。安全边界可以继续本地失败关闭，但模型不可用不能再被在线指标掩盖。

## 3. 本地回退基线验证

在线 canary 失败后，使用同一干净源码重新运行普通开发集，确认新增门禁没有改变既有本地回退能力：

```text
开发集 runId             20261021082107
sourceRevision            581be2b051e430b4311c256f1d8a0e1ece3e8c6a
sourceDirty               false
主意图                    59/59
语义操作                  31/31
完整标签契约              59/59
unsafeExecutableCount     0
infrastructureFailures    0
recognitionSources        local_fallback=59
recognitionPaths          local_guard=5, local_unavailable=54
```

这里的 `local_unavailable` 是本地回退运行的真实来源状态，不是在线结果。阶段 8C 的保留集结果仍为
19/29；本阶段没有在 canary 失败后重跑保留集，避免消耗保留集并保持既定治理边界。

## 4. 回归与安全门禁

```text
IntentRecognitionServiceTest    67 tests，0 failures，0 errors
Java reactor                    659 tests，0 failures，0 errors
干净源码开发集                  59/59
干净源码执行语义契约            87/87
契约基线 SHA-256                bd0d4129be28ccb229a5d33acdd19bf5a530116a85c77b004b79b814ba71b8f9
静态评测脚本                    9 个 PowerShell 脚本解析通过
git diff --check                通过
```

87 项契约报告 runId 为 `20261021082140`，来源提交干净且无实际执行。测试使用隔离 Compose 项目、
回环端口和测试数据库，不读取生产 `.env`，未改变生产配置、服务、账号角色、动作 allowlist 或数据。

## 5. 恢复条件与下一步

先在本机恢复 DeepSeek 账号余额，或将新的有效密钥写入 Git 忽略且权限受控的
`test/rag-e2e/.env.local`。不要在聊天、命令输出或提交中粘贴密钥。随后执行：

```powershell
pwsh -File test/rag-e2e/run-evaluation.ps1 `
  -Suite recognition-dev `
  -RequireOnlineRecognition
```

只有 canary 和在线开发集均通过后，才运行一次在线保留集：

```powershell
pwsh -File test/rag-e2e/run-evaluation.ps1 `
  -Suite recognition-holdout `
  -RequireOnlineRecognition
```

最后使用两份对应的 metrics 报告写入独立在线基线：

```powershell
pwsh -File test/rag-e2e/write-recognition-baseline.ps1 `
  -Lane deepseek-online `
  -DevelopmentMetricsPath <development.metrics.json> `
  -HoldoutMetricsPath <holdout.metrics.json>
```

若 canary 仍失败，应停止在账号或上游可用性问题，不进入 Prompt、规则或保留集调优。生产继续保持：

```text
admin-single + RAG_ADMIN + FOLDER_CREATE
```
