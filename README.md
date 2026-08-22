# Alicia Cloud Storage

一个基于 Spring Boot + React + MySQL + 腾讯云 COS 的轻量云盘项目，支持账号权限、文件管理、回收站、大文件分片上传、背景图个性化和管理员配额管理。

## 项目特点

- 支持邮箱验证码自助注册，也支持管理员统一创建账号
- 支持统一身份登录鉴权、个人资料修改、密码修改、管理员重置他人密码
- 支持 Identity 审计日志记录、管理员查询和 Web 管理页筛选
- 文件二进制内容存储在腾讯云 COS，MySQL 存储文件和文件夹元数据
- 支持文件列表、搜索、类型筛选、分页、排序
- 支持新建文件夹、上传、下载、重命名、移动
- 支持回收站、批量移动、批量删除、批量恢复、彻底删除
- 支持图片、PDF、文本、音视频预览
- 支持普通上传和大文件分片上传
- 支持断点续传、上传队列、总进度、单文件进度、失败继续、取消当前上传
- 普通用户按个人配额校验上传空间，管理员账号不受个人配额限制
- 支持用户头像和主页背景图上传，并与账号关联
- 支持 Android APK 版本发布，安装包存储在 COS，后端保留版本记录和短期签名下载入口

## 技术栈

- 后端：Spring Boot 4、Spring Web、Spring Data JPA、Flyway
- 前端：React 19、Vite 8、Ant Design 6
- 数据库：MySQL 8
- 对象存储：Tencent Cloud COS
- 部署：Docker Compose、Nginx

## 目录结构

```text
AliciaCloudStorage/
├─ CloudStorageApi/      # 云盘业务后端，消费 Identity token 并聚合云盘资料
├─ identityApi/          # 统一身份服务，负责登录、注册、Token 和账号资料
├─ rag/                  # RAG 语义服务
├─ CloudStorageDB/       # 早期 SQL 初始化脚本
├─ webApp/               # React + Vite 前端
├─ phoneApp/             # Android 客户端（Kotlin + Jetpack Compose）
├─ compose.yaml          # 本地 / 首发用 Docker Compose
├─ .env.example          # 环境变量示例
└─ pom.xml               # Maven 根工程
```

## 运行前准备

建议准备以下环境：

- Docker Desktop 或 Docker Engine + Docker Compose Plugin
- 可用的腾讯云 COS 密钥、地域和桶名

如果你希望使用下文的“开发模式”单独运行前后端，再额外准备：

- JDK 21
- Node.js 20+ 和 npm

先复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

或：

```bash
cp .env.example .env
```

至少需要正确填写这些值：

- `MYSQL_ROOT_PASSWORD`
- `ALICIA_AUTH_TOKEN_SECRET`
- `ALICIA_SHARE_ACCESS_TOKEN_SECRET`
- `ALICIA_COS_SECRET_ID`
- `ALICIA_COS_SECRET_KEY`
- `ALICIA_COS_REGION`
- `ALICIA_COS_BUCKET`

如果配置了 COS 自定义源站域名，可以额外填写：

- `ALICIA_COS_CUSTOM_DOMAIN`：预览/下载的预签名 URL 会使用该域名，例如 `files.windwindwind-alicia.cn`。

如果数据库是全新的空库，首次启动前还建议额外填写：

- `ALICIA_BOOTSTRAP_ADMIN_PHONE`
- `ALICIA_BOOTSTRAP_ADMIN_PASSWORD`

## 最快启动方式

当前仓库已经改成多阶段 Docker 构建，最省事的启动方式是直接使用 Docker Compose。

`frontend` 容器会加入外部网关网络，用于把主域名 `/` 代理到工具主站。首次启动前先创建一次：

```powershell
docker network create alicia_gateway
```

### 1. 启动容器

```powershell
docker compose up -d --build
```

共享库过渡期里，`api` 容器会先完成 CloudStorageApi 历史迁移，随后 `identity` 容器执行自己的 Flyway 迁移并进行 JPA validate。

启动后默认地址：

- 前端：`http://localhost/cloudPan/`
- 主域名根入口：`http://localhost/` 会代理到工具主站容器
- 统一登录入口：`http://localhost/login`
- 后端（仅本机回环）：`http://127.0.0.1:8090`
- 健康检查（仅本机回环）：`http://127.0.0.1:8090/api/health`
- RAG（仅本机回环）：`http://127.0.0.1:8091`
- 同域 RAG 入口：`http://localhost/rag/api/health`
- Identity API（仅本机回环）：`http://127.0.0.1:8093`
- 同域 Identity 公开入口：`http://localhost/api/identity/health`
- MySQL（仅本机回环）：`127.0.0.1:3310`

查看运行状态：

```powershell
docker compose ps
docker compose logs -f api
```

验证 Identity API：

```powershell
curl http://127.0.0.1:8093/api/identity/health
curl http://localhost/api/identity/health
curl http://127.0.0.1:8093/api/identity/internal/users/1
```

`/api/identity/internal/**` 只允许通过内网回环端口验证，不经 Nginx 对公网开放。

验证 Identity API 内部登录：

```powershell
curl -X POST http://127.0.0.1:8093/api/identity/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"identifier\":\"你的账号或邮箱\",\"password\":\"你的密码\"}"
```

验证 Identity API 内部当前用户读取：

```powershell
curl http://127.0.0.1:8093/api/identity/auth/me `
  -H "Authorization: Bearer 你的_identity_token"
```

验证 Identity API 内部 Token 续签和注销：

```powershell
curl -X POST http://127.0.0.1:8093/api/identity/auth/token/refresh `
  -H "Authorization: Bearer 你的_identity_token" `
  -H "Content-Type: application/json" `
  -d "{\"refreshToken\":\"你的_identity_refresh_token\"}"

curl http://127.0.0.1:8093/api/identity/auth/sessions `
  -H "Authorization: Bearer 你的_identity_token"

curl -X DELETE http://127.0.0.1:8093/api/identity/auth/sessions/你的_session_id `
  -H "Authorization: Bearer 你的_identity_token"

curl -X POST http://127.0.0.1:8093/api/identity/auth/logout `
  -H "Authorization: Bearer 你的_identity_token" `
  -H "Content-Type: application/json" `
  -d "{\"refreshToken\":\"你的_identity_refresh_token\"}"
```

验证 Identity API 内部邮箱注册：

```powershell
curl -X POST http://127.0.0.1:8093/api/identity/auth/register/email-code `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"你的邮箱\"}"

curl -X POST http://127.0.0.1:8093/api/identity/auth/register/verify `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"你的邮箱\",\"code\":\"邮箱验证码\",\"nickname\":\"昵称\",\"password\":\"密码\"}"
```

当前公网身份入口为 `/api/identity/auth/**` 和 `/api/identity/admin/**`；登录、注册、Token 校验、续签、注销、刷新会话查询/撤销、密码和账号资料写入已经由 `identityApi` 执行。登录和邮箱注册验证会返回 `token` 与 `refreshToken`，其中新签发的 access token 是 HS256 JWT，旧两段式 token 只保留解析兼容；续签接口优先使用 `refreshToken` 轮换会话，未携带 `refreshToken` 时仍兼容 Authorization 续签并补发新刷新会话。`CloudStorageApi` 负责补齐云盘资料，并通过 `/api/cloud-profile/**` 返回云盘聚合资料、头像和主页背景。

`identityApi` 已启用独立 Flyway，迁移文件位于 `identityApi/src/main/resources/db/identity-migration`，迁移历史表为 `identity_flyway_schema_history`。当前仍共用同一个 MySQL 数据库，CloudStorageApi 早期 V1-V14 历史迁移继续保留，后续身份表结构变更应新增到 `identityApi`。CloudStorageApi 和 identityApi 测试中已有双向迁移边界检查，防止新的身份结构变更写回云盘迁移目录，也防止云盘业务结构进入 Identity 迁移目录。

云盘 Web 的纯身份写操作走同域 Identity 公开入口，例如 `/api/identity/auth/profile`、`/api/identity/auth/password` 和 `/api/identity/admin/users/{userId}/password`；Identity 管理员审计日志只读查询走 `/api/identity/admin/audit-logs`。头像上传、头像访问、当前用户云盘聚合资料由 `CloudStorageApi` 的 `/api/cloud-profile/me`、`/api/cloud-profile/avatar` 和 `/api/cloud-profile/avatar/{userId}` 提供。管理员云盘聚合用户列表和创建入口统一为 `/api/admin/cloud-users`，云盘容量调整为 `/api/admin/cloud-users/{userId}/quota`。

云盘 Web 会在启动和运行中通过 `/api/identity/auth/token/refresh` 续签登录态，主动退出登录时调用 `/api/identity/auth/logout` 后再清理本地 token 和 refresh token；Android 客户端恢复会话和退出登录也使用同一套 Identity 接口。默认 logout 只撤销当前刷新会话；需要全设备退出时请求体传 `{"allDevices":true}`。`GET /api/identity/auth/sessions` 返回当前账号刷新会话元数据，不暴露 refresh token 或 token hash；`DELETE /api/identity/auth/sessions/{sessionId}` 只允许撤销当前账号自己的会话。云盘 Web 头像菜单已提供“登录会话”入口，可查看会话并撤销非当前有效会话。

`identityApi` 新注册用户首次携带 identity token 访问 CloudStorageApi 受保护接口时，CloudStorageApi 会自动补建对应的 `cloud_user_profile`，云盘默认额度取 `ALICIA_STORAGE_DEFAULT_USER_QUOTA_BYTES`。

### HTTPS 部署（已签发证书后）

如果你已经拿到 Nginx 证书文件，可以把证书和私钥分别放到：

- `deploy/certs/fullchain.pem`
- `deploy/certs/privkey.pem`

然后使用 HTTPS 覆盖配置启动前端：

```powershell
docker compose -f compose.yaml -f compose.https.yaml up -d --build frontend
```

这样会额外开放 `443`，并将 `http://` 请求自动跳转到 `https://`。统一登录入口为 `https://windwindwind-alicia.cn/login`，云盘 Web 入口为 `https://windwindwind-alicia.cn/cloudPan/`，正式 RAG 入口为 `https://windwindwind-alicia.cn/rag`，SSE 请求由 Nginx 直通 `rag` 容器；Identity 公开入口为 `https://windwindwind-alicia.cn/api/identity/health`、`/api/identity/auth/**` 和 `/api/identity/admin/**`。`/rag/internal/` 与 `/api/identity/internal/**` 不对公网开放。

生产服务器更新 RAG 与 Nginx 时，在仓库内执行：

```bash
bash deploy/scripts/update-rag-production.sh
```

脚本会拒绝覆盖服务端已有的 tracked 改动，确认 `.env` 已配置 DeepSeek，快进拉取 `main`，重建 `rag` 与 `frontend`，最后同时检查 `127.0.0.1:8091/api/health` 和公网 `/rag/api/health`。密钥只保留在服务器 `.env`，不会输出到日志。

生产更新 `api`、`identity` 或前端路由后，可以使用统一回归脚本检查主域路径边界、登录续签、刷新会话查询、云盘聚合资料、存储概览、管理员云盘用户入口、Identity 审计日志查询、Identity Flyway 迁移历史、旧身份路径 404、注销失效和审计日志最新行：

```bash
bash deploy/scripts/verify-identity-cloud-routes.sh
```

脚本默认验证 `http://127.0.0.1:8090`、`http://127.0.0.1:8093` 和 `https://127.0.0.1`；如需改为域名验证，可设置 `ALICIA_PUBLIC_BASE_URL=https://windwindwind-alicia.cn`。完整管理员检查需要使用管理员账号；普通账号可临时设置 `ALICIA_VERIFY_SKIP_ADMIN_CHECK=true`。

生产服务器建议把 `origin` 指向 Gitee，以减少 GitHub 连接失败造成的更新中断：

```bash
cd ~/aliciaCloudStorage
git remote set-url origin https://gitee.com/zengLi199447/cloud-alicia.git
git fetch origin main
git pull --ff-only origin main
```

## 开发模式

如果你希望前后端分开开发，推荐这样启动：

### 1. 先只启动 MySQL

```powershell
docker compose up -d db
```

### 2. 启动后端

后端默认连接 `localhost:3306`，如果你使用的是 `compose` 里的 MySQL，需要把数据源改到 `3310`。

PowerShell 示例：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3310/alicia_cloud_storage?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="123456"
$env:ALICIA_IDENTITY_API_BASE_URL="http://localhost:8093"
$env:ALICIA_SHARE_ACCESS_TOKEN_SECRET="replace-this-with-a-long-random-secret"
$env:ALICIA_COS_SECRET_ID="your-secret-id"
$env:ALICIA_COS_SECRET_KEY="your-secret-key"
$env:ALICIA_COS_REGION="ap-shanghai"
$env:ALICIA_COS_BUCKET="your-bucket-appid"
$env:ALICIA_COS_CUSTOM_DOMAIN="files.windwindwind-alicia.cn"
$env:ALICIA_APP_PACKAGE_COS_PREFIX="app-packages"

Set-Location CloudStorageApi
..\mvnw spring-boot:run
```

### 3. 启动前端

```powershell
Set-Location webApp
npm ci
npm run dev
```

前端开发地址：

- `http://localhost:5173/cloudPan/`

## 首个管理员初始化

当前仓库不再内置默认演示账号，也不再依赖固定的演示管理员密码。

如果数据库为空，并且你希望首次启动时自动创建第一个管理员，请在 `.env` 中显式填写：

```env
ALICIA_BOOTSTRAP_ADMIN_PHONE=你的管理员手机号
ALICIA_BOOTSTRAP_ADMIN_PASSWORD=你自己设置的强密码
ALICIA_BOOTSTRAP_ADMIN_NICKNAME=系统管理员
ALICIA_BOOTSTRAP_ADMIN_AVATAR_URL=
```

如果这两个核心变量留空，系统会正常启动，但不会自动生成任何账号。

建议：

- 首次登录后立刻修改管理员密码
- 至少再创建一个正式管理员账号备用
- 空库初始化完成后，按需清空 `ALICIA_BOOTSTRAP_ADMIN_PHONE` 和 `ALICIA_BOOTSTRAP_ADMIN_PASSWORD`

## 常用命令

本地提交后同步到 Gitee 和 GitHub：

```powershell
.\deploy\scripts\push-all-remotes.ps1
```

等价于：

```powershell
git push gitee main
git push origin main
```

后端测试：

```powershell
.\mvnw -pl CloudStorageApi test
```

全部后端模块测试：

```powershell
.\mvnw -pl CloudStorageApi,identityApi,rag test
```

前端构建检查：

```powershell
Set-Location webApp
npm run build
```

停止容器：

```powershell
docker compose down
```

