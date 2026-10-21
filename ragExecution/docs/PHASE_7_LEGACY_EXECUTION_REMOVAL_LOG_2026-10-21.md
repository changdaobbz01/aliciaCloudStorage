# RAG Execution 阶段 7 旧执行路径清理完成日志｜2026-10-21

## 1. 结论

阶段 7 的源码清理、完整回归、Android 真机验证、生产发布和主动无写入验收均已完成。

当前边界保持不变：

```text
生产阶段       admin-single
允许角色       RAG_ADMIN
允许动作       FOLDER_CREATE
普通用户       不开放云端写执行
其他动作       不开放
客户端职责     确认、取消、状态展示和本地文件输入
写操作执行者   ragExecution -> CloudStorageApi
```

Android 公共下载通道已发布并独立下载验证 `0.1.14 (15)`。该分发没有改变服务端灰度范围，也没有恢复任何旧客户端执行路径。

## 2. 实现结果

源码提交：

```text
0e393df Remove legacy Android RAG execution path
```

主要变更：

- 删除 Android `RagActionExecutor`、`RagActionBridgeValidator`、对应测试和本地执行 BuildConfig/安装脚本开关。
- Android 只确认、取消和查询服务端 `executionReference`；旧草稿没有云端任务引用时明确阻断，不再回退到本地写执行。
- RAG 先用完整类型化草稿在服务端登记任务，再统一清理同步与 SSE 公共响应。
- 公共 `backendActionDraft` 不再输出 `method`、`pathTemplate`、`path`、`contentType`、`pathVariables`、`queryParameters` 或 `body`，并固定为不可直接执行。
- 移动端契约升级为 `mobile_contract_v2`；架构测试禁止重新引入本地 RAG 写执行路径。
- 发布就绪检查与生产主动验证脚本同步到新契约，不再依赖已删除的兼容开关。

## 3. 本地回归

```text
rag                  308 tests，0 failures，0 errors
ragExecution          53 tests，0 failures，0 errors
CloudStorageApi      272 tests，0 failures，0 errors
Android Debug        158 tests，通过
Android Release      158 tests，通过
Android Debug build  通过
Android Release build 通过
Compose config       通过
git diff --check     通过
```

Android 发布就绪检查再次验证：

```text
[OK] phoneAppAdd RAG cloud execution client boundary is release-ready and default-off
[OK] phoneAppAdd identity session contract is release-ready
Alicia Android release readiness check passed.
```

## 4. Android 真机验收

设备：`M2011K2C / Android 13 / ADB 9daf5ab9`

```text
connected instrumentation  2/2 通过
设备 versionName            0.1.14
设备 versionCode            15
```

小米系统的 USB 安装确认曾使 Gradle UTP 返回 `INSTALL_FAILED_USER_RESTRICTED`；在设备上确认安装后，以同一测试 APK 直接运行 instrumentation 通过。该问题属于设备安装授权，不是应用测试失败。

测试清理阶段曾卸载目标包，因此真机上的旧登录状态可能已被清除；设备已重新安装 `0.1.14 (15)`，后续使用时可能需要重新登录。

## 5. 生产发布与验证

生产已发布提交 `0e393df`。发布后的标准验收结果：

```text
[OK] stage configuration is explicit and internally consistent
[OK] Compose configuration resolves with the candidate env
[OK] local liveness reports the expected admin-single feature state
[OK] required runtime dependencies are healthy
[OK] public health is reachable and both internal route families are blocked
[OK] container is healthy with restart count 0
RAG execution production verification passed: stage=admin-single
```

2026-10-21 收尾复核：

```text
remote commit              0e393df
RAG /api/health            ok
RAG dependency health      Identity ok，CloudStorageApi ok
RAG restarts               0
RAG Execution health       healthy
RAG Execution restarts     0
```

生产发布启动窗口出现过短暂连接重置；容器稳定后标准发布脚本和独立复核均通过，没有持续健康异常或重启。

## 6. 主动无写入验收

使用专用 `RAG_ADMIN` 测试账号，经公网链路完成四类计划登记与取消：

```text
Create a folder named <name>
请在根目录创建一个名为 <name> 的文件夹
创建一个文件夹，名称是 <name>
Please create a directory called <name>
```

每个用例均验证：

- 识别结果为 `folder_create`，目录名完整保留。
- 服务端产生不可变 `executionReference`。
- 公共响应只含不可执行动作元数据，不暴露旧路由和请求体字段。
- 取消后状态为 `CANCELLED`、步骤 `attempts=0`。
- 仅有一个 `EXECUTION_CANCELLED` 事件。
- 没有 Cloud 动作回执，没有创建存储节点。
- 临时 Identity 会话正常注销。

最终结果：

```text
RAG admin active verification passed: cases=4 executeAndCleanup=False
```

本轮未执行真实写入、未扩大角色、未扩大动作 allowlist。

## 7. Android 正式包

已生成并验证的正式签名包：

```text
versionName   0.1.14
versionCode   15
file          alicia-cloud-android-0.1.14-15-release.apk
size          27601533 bytes
sha256        6d9a803d1ed563d31d8caca1ce612dd2396a2d659e539787eee5fbe8ee28292d
signature     APK Signature Scheme v3，Alicia release certificate
```

本地生成目录：

```text
deploy/generated/android-release-packages/phoneAppAdd/0.1.14-15-release-20261021101038
```

该目录被 Git 忽略，不提交签名产物。公共上传使用全局 `ADMIN`/`CLOUD_ADMIN` 临时会话完成，专用 `RAG_ADMIN` 账号没有代替发布权限；上传后临时 Identity 会话已正常注销，本地临时发布凭据文件已清除。

公共分发最终结果：

```text
public version    0.1.14
public uploadedAt 2026-10-21T02:16:08.790695793Z
download bytes    27601533
download sha256   6d9a803d1ed563d31d8caca1ce612dd2396a2d659e539787eee5fbe8ee28292d
hash matches      true
```

公网 APK 由新请求完整下载后重新计算 SHA-256，不只依赖上传响应或 HEAD 检查。发布后再次运行生产标准验证，`admin-single + FOLDER_CREATE`、依赖健康、内部路由屏蔽和容器重启检查全部通过。

## 8. 后续进度线

阶段 7 的服务端迁移与旧路径清理已经闭环。下一阶段为独立的识别能力评测与优化，不与执行通道改造混做：

1. 固化可重复的意图识别评测集和评分口径。
2. 建立当前规则、模型、检索与多轮上下文的基线结果。
3. 按错误类别优化，并持续运行安全执行契约回归。
4. 在单独批准前继续保持 `admin-single + RAG_ADMIN + FOLDER_CREATE`。
