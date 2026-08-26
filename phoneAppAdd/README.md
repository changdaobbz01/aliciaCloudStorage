# Alicia Cloud Android Add

`phoneAppAdd` 是 Alicia 云盘移动端的新视觉版本，使用 Kotlin + Jetpack Compose 构建。业务层、API、ViewModel 状态机复用当前 `phoneApp`，UI 改为轻量白底蓝色体系，便于和机甲风版本并行维护。

## 当前能力

- 手机号 + 密码登录
- 首页概览、空间使用进度、最近文件、文件分类入口
- 文件页：目录浏览、全盘分类、关键字搜索、文件/文件夹筛选
- 文件操作：自建文件/文件夹选择页、批量逐文件上传、新建文件夹、预览、下载、分享、移动、删除到回收站
- 批量操作：多选下载 ZIP、移动、删除、回收站恢复和彻底删除
- 传输管理：底部导航独立页面、下载/上传进度、失败态、下载失败重试
- 分享链接：剪贴板/深链识别、提取码校验、选中内容保存到网盘
- 管理员页：创建账号、调整额度、重置密码
- 账号面板：头像、昵称、密码、正式服务接入、退出登录
- 登录态：启动时使用 refresh token 续签，缺失或失效时清理本地会话并提示重新登录

这个工程使用独立 `applicationId = "com.alicia.cloudstorage.phone.add"`，可以和当前 `phoneApp` 同时安装，便于对比两套 UI。源码 `namespace` 仍保持 `com.alicia.cloudstorage.phone`，避免为了并行安装而移动业务代码目录。

并行体验版保留内置 APP 更新检测，便于验证新 UI 版本的完整启动流程。

## 默认联调地址

当前默认连线上服务：

- 云盘 API：`https://windwindwind-alicia.cn`
- RAG：`https://windwindwind-alicia.cn/rag`

如需覆盖默认地址，可通过 Gradle 属性或 `local.properties` 配置 `ALICIA_API_BASE_URL`。

普通的 `ALICIA_API_BASE_URL`、`ALICIA_RAG_BASE_URL` 和执行开关只用于 Debug 构建。Release 包固定采用正式 API 与 `/rag`；确需构建其他正式环境时，必须显式使用 `ALICIA_RELEASE_API_BASE_URL`、`ALICIA_RELEASE_RAG_BASE_URL` 和 `ALICIA_RELEASE_RAG_ACTION_EXECUTION_ENABLED`，避免本地地址意外进入发布包。

## 本地运行

1. 在 Android Studio 中打开 `phoneAppAdd`
2. 如需覆盖默认后端地址，可参考 `local.properties.example`
3. 同步 Gradle 并运行 `app`

## 可选本地配置

可以在 `phoneAppAdd/local.properties` 里追加：

```properties
ALICIA_API_BASE_URL=https://windwindwind-alicia.cn
```

如果你要临时切回本地开发环境，也可以改成例如：

```properties
ALICIA_API_BASE_URL=http://10.0.2.2:8090
ALICIA_RAG_BASE_URL=http://10.0.2.2:8091
ALICIA_RAG_ACTION_EXECUTION_ENABLED=false
ALICIA_RAG_CONFIRMATION_MESSAGE=确认
```

如果连接的是 USB 真机，将 `ALICIA_RAG_BASE_URL` 配为 `http://127.0.0.1:8081` 后，使用下面的脚本安装。它会检查本地 RAG、安装 Debug 包、重建 `adb reverse`，并从设备侧验证健康接口：

```powershell
.\scripts\install-debug-device.ps1
```

手机重连或重启后，`adb reverse` 可能失效。只恢复连接而不重新安装时可执行：

```powershell
.\scripts\install-debug-device.ps1 -SkipInstall
```

需要在真机验证云端 RAG 时，不必修改或删除本地 `local.properties`。使用云端安装脚本即可让本次 Debug 构建显式采用正式地址，并移除设备上的 RAG 端口反向映射，避免本地服务掩盖云端问题：

```powershell
.\scripts\install-cloud-device.ps1
```

发版前建议在仓库根目录运行统一 readiness 检查，它会同时覆盖 `phoneApp` 与 `phoneAppAdd` 的正式服务入口、Identity refresh/logout 契约和登录态过期处理：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/check-android-release-readiness.ps1
```

准备新视觉 `phoneAppAdd` 的 APK 发版包时，在仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/prepare-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

脚本会构建 Release APK，输出到 `deploy/generated/android-release-packages/`，并生成 SHA-256、发布清单、`release-notes.txt` 和管理员上传 helper。上传前必须确认生成目录内的 `release-notes.txt` 已改为正式更新说明。

正式环境的 RAG 健康检查地址是 `https://windwindwind-alicia.cn/rag/api/health`。本地仍使用 `http://127.0.0.1:8081/api/health`，两者互不覆盖。

独立启动 RAG 时，还必须给 RAG 进程配置可信的 CloudStorageApi 地址，否则文件查询和目标目录匹配会被安全地跳过。例如移动端连接线上 API 时：

```powershell
$env:ALICIA_STORAGE_API_BASE_URL="https://windwindwind-alicia.cn"
.\mvnw.cmd -pl rag spring-boot:run
```

Docker Compose 环境使用 `http://api:8080`。这个地址只能由服务端部署配置，不能由移动端请求动态指定，避免把用户登录令牌转发到不可信地址。

本地启动前需要确保当前终端进程真正继承了 DeepSeek 配置。若密钥保存在 Windows 用户级环境变量中，可先同步到当前终端，再启动 RAG：

```powershell
$env:DEEPSEEK_API_KEY=[Environment]::GetEnvironmentVariable("DEEPSEEK_API_KEY", "User")
$env:ALICIA_STORAGE_API_BASE_URL="https://windwindwind-alicia.cn"
.\mvnw.cmd -pl rag spring-boot:run
```

启动后访问 `http://127.0.0.1:8081/api/health`。其中 `deepseekConfigured` 和 `storageApiConfigured` 都应为 `true`；该接口只返回配置状态，不返回任何密钥。目录列举默认最多返回 50 项，可通过 `ALICIA_RAG_CANDIDATE_BINDING_DIRECTORY_LIST_MAX_RESULTS` 调整。

`ALICIA_RAG_ACTION_EXECUTION_ENABLED` 默认必须保持 `false`。它只控制 AI 文件操作执行器是否真的提交到 CloudStorageApi；聊天、候选展示、候选选择和最终确认 UI 不依赖这个开关。正式打开前需要完成候选选择、最终确认、本地 allowlist 和后端鉴权验收。

`ALICIA_RAG_CONFIRMATION_MESSAGE` 用于配置用户点击“确认计划”后，移动端发给 RAG 的确认短语；RAG 会根据该短语继续生成受控的 `backendActionDraft`。

## 后续建议

- 正式发版前接入与当前 `phoneApp` release 包一致的签名流程
- 按新 UI 风格继续细化 PDF / 音视频内嵌预览
- 增加大文件分片上传
- 引入更完整的分页、离线缓存和分类统计
