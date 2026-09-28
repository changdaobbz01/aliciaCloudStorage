# Alicia Cloud Android Add

`phoneAppAdd` 是 Alicia 云盘移动端的新视觉正式版本，使用 Kotlin + Jetpack Compose 构建。业务层、API、ViewModel 状态机复用当前 `phoneApp`，UI 改为轻量白底蓝色体系，并作为 `/api/app-package/download/current` 的默认 APK 来源。

## 当前能力

- 手机号或邮箱 + 密码登录
- 邮箱验证码注册并自动登录
- 首页概览、空间使用进度、最近文件、文件分类入口
- 文件页：目录浏览、全盘分类、关键字搜索、文件/文件夹筛选
- 文件操作：自建文件/文件夹选择页、批量逐文件上传、新建文件夹、预览、下载、分享、移动、删除到回收站
- 批量操作：多选下载 ZIP、移动、删除、回收站恢复和彻底删除
- 操作边界：批量移动/删除/恢复/彻底删除最多 500 项，ZIP 打包下载最多 100 项，单个分享最多 20 项，分享保存最多 500 项
- 传输管理：底部导航独立页面、下载/上传进度、失败态、下载失败重试
- 分享链接：剪贴板/深链识别、提取码校验、选中内容保存到网盘，并保留分享文件/文件夹下载协议能力
- 管理员页：创建账号、调整额度、重置密码
- 账号面板：头像、昵称、密码、正式服务接入、退出登录
- 登录态：启动时使用 refresh token 续签，缺失或失效时清理本地会话并提示重新登录

这个工程使用正式 `applicationId = "com.alicia.cloudstorage.phone"`，用于覆盖旧移动端并承接后续更新。源码 `namespace` 仍保持 `com.alicia.cloudstorage.phone`。

正式版本保留内置 APP 更新检测，便于用户收到后续版本更新提示。

## 默认联调地址

当前默认连线上服务：

- 云盘 API：`https://windwindwind-alicia.cn`
- RAG：`https://windwindwind-alicia.cn/rag`

如需覆盖默认地址，可通过 Gradle 属性或 `local.properties` 配置 `ALICIA_API_BASE_URL`。

普通的 `ALICIA_API_BASE_URL`、`ALICIA_RAG_BASE_URL`、`ALICIA_RAG_EXECUTION_BASE_URL` 和执行开关只用于 Debug 构建。Release 包固定采用正式 API、`/rag` 与 `/rag-execution`；确需构建其他正式环境时，必须显式使用 `ALICIA_RELEASE_API_BASE_URL`、`ALICIA_RELEASE_RAG_BASE_URL`、`ALICIA_RELEASE_RAG_EXECUTION_BASE_URL`、`ALICIA_RELEASE_RAG_CLOUD_EXECUTION_ENABLED` 和旧路径兼容开关 `ALICIA_RELEASE_RAG_ACTION_EXECUTION_ENABLED`，避免本地地址或开发开关意外进入发布包。

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
ALICIA_RAG_EXECUTION_BASE_URL=http://10.0.2.2:8094
ALICIA_RAG_CLOUD_EXECUTION_ENABLED=false
ALICIA_RAG_ACTION_EXECUTION_ENABLED=false
ALICIA_RAG_CONFIRMATION_MESSAGE=确认
```

如果连接的是 USB 真机，将 `ALICIA_RAG_BASE_URL` 配为 `http://127.0.0.1:8081` 后，使用下面的脚本安装。它会检查本地 RAG、安装 Debug 包、重建 `adb reverse`，并从设备侧验证健康接口；脚本可以从仓库根目录或 `phoneAppAdd` 目录调用。需要联调新云端执行通道时追加 `-EnableCloudExecution`；脚本会同时检查 `ragExecution` 的 `8084` 端口并显式打开本次 Debug 构建的云端执行开关，同时强制关闭旧本地动作执行开关，避免两个执行器并行生效：

```powershell
.\scripts\install-debug-device.ps1
.\scripts\install-debug-device.ps1 -EnableCloudExecution
```

手机重连或重启后，`adb reverse` 可能失效。只恢复连接而不重新安装时可执行：

```powershell
.\scripts\install-debug-device.ps1 -SkipInstall
```

需要在真机验证云端 RAG 时，不必修改或删除本地 `local.properties`。使用云端安装脚本即可让本次 Debug 构建显式采用正式地址，并移除设备上的 RAG 端口反向映射，避免本地服务掩盖云端问题；脚本同样与当前调用目录无关。两个执行开关默认都关闭，只有显式传入对应开关才会为本次构建开启：

```powershell
.\scripts\install-cloud-device.ps1
.\scripts\install-cloud-device.ps1 -EnableCloudExecution
```

发版前建议在仓库根目录运行统一 readiness 检查，它会同时覆盖 `phoneApp` 与 `phoneAppAdd` 的正式服务入口、Identity refresh/logout 契约和登录态过期处理：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/check-android-release-readiness.ps1
```

准备正式下载 APK 时，在仓库根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/generate-android-release-keystore.ps1
. deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/prepare-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

首次发版先生成 Android release keystore，并妥善备份 `deploy/generated/android-signing/` 下的私有文件。之后加载生成的 `android-release-signing.env.ps1`，准备脚本会构建并签名 `phoneAppAdd` Release APK，确认正式包名为 `com.alicia.cloudstorage.phone`，输出到 `deploy/generated/android-release-packages/`，并生成 SHA-256、发布清单、`release-notes.txt` 和管理员上传 helper。

需要直接更新服务器上的当前 APK 时，用一键发布脚本：

```powershell
. deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

脚本默认发布 `phoneAppAdd`，会打包签名、登录 Identity 管理员账号、上传 `/api/admin/app-package`，并校验 `/api/app-package/version` 和 `/api/app-package/download/current`。如需发布已准备好的目录，可追加 `-SkipPrepare -PackageDir deploy/generated/android-release-packages/phoneAppAdd/<dir>`。

如果希望服务器执行云盘更新命令时顺带发布 Git 里的 APK，先把本地已签名产物整理到仓库当前发布目录：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/stage-android-git-package.ps1
```

脚本会写入 `deploy/android-app-package/current.apk`、`version-name.txt`、`release-notes.txt` 和 `current.apk.sha256`。提交推送后，服务器侧 `deploy/scripts/update-cloud-production.sh` 会在默认 `ALICIA_PUBLISH_ANDROID_APP_PACKAGE=auto` 下检测并发布这个 APK。

正式环境的 RAG 健康检查地址是 `https://windwindwind-alicia.cn/rag/api/health`，执行服务依赖健康地址是 `https://windwindwind-alicia.cn/rag-execution/api/health/dependencies`。本地分别使用 `http://127.0.0.1:8081/api/health` 与 `http://127.0.0.1:8084/api/health/dependencies`，两者互不覆盖。

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

`ALICIA_RAG_CLOUD_EXECUTION_ENABLED` 控制新的云端执行确认路径，默认必须保持 `false`。启用后，确认按钮只向 `ragExecution` 提交 `executionId + expectedVersion`，通过状态查询展示排队、执行、重试和终态；客户端不会重新提交 ActionPlan，也不会解释服务端 method/path/body。任务确认后由云端持久化执行，离开页面或客户端网络中断不会撤销任务。

`ALICIA_RAG_ACTION_EXECUTION_ENABLED` 仅保留旧 `RagActionExecutor` 的一个兼容发布周期，默认必须保持 `false`。它只可能在云端模式关闭时处理没有 `executionReference` 的旧响应；只要云端模式开启，缺失任务引用、通道关闭或请求失败都不会自动回退到本地写操作。上传本地文件、页面导航、预览和系统分享仍属于客户端能力。

`ALICIA_RAG_CONFIRMATION_MESSAGE` 用于配置用户点击“确认计划”后，移动端发给 RAG 的确认短语；RAG 会根据该短语继续生成受控的 `backendActionDraft`。

## 后续建议

- 使用同一 Android release keystore 持续发版，保证后续 APK 可以平滑升级
- 按新 UI 风格继续细化 PDF / 音视频内嵌预览
- 增加大文件分片上传
- 引入更完整的分页、离线缓存和分类统计
