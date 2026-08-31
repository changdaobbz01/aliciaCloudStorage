# Alicia Cloud Storage

一个基于 Spring Boot + React + MySQL + 腾讯云 COS 的轻量云盘项目，支持账号权限、文件管理、回收站、大文件分片上传、背景图个性化和管理员配额管理。

## 项目特点

- 支持邮箱验证码自助注册，也支持管理员统一创建账号
- 支持统一身份登录鉴权、个人资料修改、密码修改、管理员重置他人密码
- 支持 Identity 全局角色和应用级角色，云盘管理员权限由 `cloud=CLOUD_ADMIN` 表达，RAG 角色族由 `rag/RAG_*` 表达
- 支持 Identity 审计日志记录、管理员查询和 Web 管理页筛选
- 文件二进制内容存储在腾讯云 COS，MySQL 存储文件和文件夹元数据
- 支持文件列表、搜索、类型筛选、分页、排序
- 支持新建文件夹、上传、下载、重命名、移动
- 支持回收站、批量移动、批量删除、批量恢复、彻底删除
- 支持图片、PDF、文本、音视频预览
- 支持分享链接、提取码访问、登录后保存分享内容和下载分享文件/文件夹压缩包
- Web 和 Android 客户端已对齐服务端文件操作边界：批量移动/删除/恢复/彻底删除最多 500 项，ZIP 打包下载最多 100 项，单个分享最多 20 项，分享保存最多 500 项
- 支持普通上传和大文件分片上传
- 支持大文件上传断点续传、上传队列、总进度、单文件进度、失败继续、取消当前上传
- 文件下载和分享下载支持标准 HTTP Range 响应，浏览器和移动端下载器可进行断点续传
- 支持 COS 对象清理补偿队列，上传/分片/分享回滚和彻底删除会登记后台重试任务，减少元数据与对象存储不一致
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
├─ webApp/               # 普通云盘用户端，挂载在 /cloudPan/
├─ sysManage/            # 云盘运营后台前端，挂载在 /console/cloud/
├─ phoneAppAdd/          # 正式 Android 客户端（Kotlin + Jetpack Compose）
├─ phoneApp/             # 历史 Android 客户端参考
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

JWT access token 代码默认仍支持 `ALICIA_AUTH_TOKEN_ALGORITHM=HS256`，元数据默认使用生产主域配置：`ALICIA_AUTH_TOKEN_ISSUER=https://windwindwind-alicia.cn`、`ALICIA_AUTH_TOKEN_AUDIENCE=alicia-tools`、`ALICIA_AUTH_TOKEN_KEY_ID=alicia-hs256-v1`。当前生产 `.env` 已在 2026-08-22 切换为 `RS256/alicia-rs256-20260822035821`，并已在 2026-08-24 移除历史 HS256 验签 key。部署到 staging、临时域名或未来做密钥轮换时，需要在 `.env` 中显式调整这些值，并同步重建 `identity` 容器。轮换 HS256 密钥时，新密钥写入 `ALICIA_AUTH_TOKEN_SECRET` 和 `ALICIA_AUTH_TOKEN_KEY_ID`，旧密钥按 `old-kid=old-secret;older-kid=older-secret` 格式放入 `ALICIA_AUTH_TOKEN_PREVIOUS_KEYS`；切换或轮换 RS256 密钥时，将 `ALICIA_AUTH_TOKEN_ALGORITHM` 设为 `RS256`，并配置 PKCS#8 私钥、X.509 公钥和新的 `kid`，公钥会通过 `/api/identity/.well-known/jwks.json` 发布。

生成 RS256 签名配置可在服务器仓库执行：

```bash
bash deploy/scripts/generate-identity-rs256-env.sh
```

脚本会把私钥、公钥和 `.env` 片段写入 `deploy/generated/identity-rs256/`，该目录已被 git 忽略。生成脚本会读取当前 `.env` 的 `ALICIA_AUTH_TOKEN_SECRET`，并在未显式配置 `ALICIA_AUTH_TOKEN_KEY_ID` 时按 compose 默认 `alicia-hs256-v1` 写入 `ALICIA_AUTH_TOKEN_PREVIOUS_KEYS`，用于有真实旧客户端时的平滑切换；当前生产已经完成 RS256 切换并移除了历史 HS256 key。后续 RS256 密钥轮换也先用该脚本生成新的 RSA key pair 和 snippet。

正式合并 `.env` 前，可以先做一次不改 `.env` 的 RS256 dry-run：

```bash
bash deploy/scripts/verify-identity-rs256-dry-run.sh
```

dry-run 会临时用最新生成的 RS256 env 片段重建 `identity`，运行统一路由验证，并默认恢复回 `.env` 中的当前配置。

dry-run 通过后，可先生成正式切换用的候选 `.env` 文件：

```bash
bash deploy/scripts/prepare-identity-rs256-cutover-env.sh
```

该脚本会在 `deploy/generated/identity-rs256/` 写入 `*.candidate.env`，并打印备份、切换和回滚命令；它不会直接覆盖生产 `.env`。如果历史 snippet 未包含 `ALICIA_AUTH_TOKEN_PREVIOUS_KEYS`，脚本会从当前 `.env` 推导旧 HS256 兼容项。

生产已完成 RS256 切换并通过统一验证：登录和续签 token 均为 `RS256/alicia-rs256-20260822035821`，JWKS 暴露当前 RSA 公钥，历史 HS256 key 已从生产环境移除，云盘聚合、存储概览、云盘管理员、审计查询、会话撤销、logout 和旧路由移除检查均通过。

生产已经处于 RS256 后，后续轮换新的 RSA 签名密钥使用独立的候选 `.env` 准备脚本：

```bash
bash deploy/scripts/prepare-identity-rs256-rotation-env.sh
```

该脚本要求当前 `.env` 已是 RS256，会读取最新生成的 RS256 snippet，把新私钥/公钥设为当前签名 key，并自动把当前 RSA 公钥加入 `ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS` 作为历史验签 key；同时打印备份、切换、验证和回滚命令，不直接覆盖生产 `.env`。旧 RSA access-token 窗口结束后，可用下面的脚本生成移除候选：

```bash
bash deploy/scripts/prepare-identity-rs256-previous-key-removal-env.sh <old-rs256-kid>
```

未来密钥轮换后需要移除历史 HS256 key 时，可以先生成候选 `.env`：

```bash
bash deploy/scripts/prepare-identity-hs256-key-removal-env.sh
```

该脚本默认准备移除 `alicia-hs256-v1`，也可以把其他历史 `kid` 作为第一个参数传入。它只生成候选 `.env` 和回滚命令，不直接改生产 `.env`。`deploy/generated/identity-rs256/` 下的 `.env`、candidate 和 backup 都按敏感文件处理，应保持 `600` 权限。

未正式上线环境可以直接使用一键 apply 脚本。它会生成候选 `.env`、备份当前 `.env`、替换配置、重启 identity、运行统一验证，并在失败时自动回滚：

```bash
bash deploy/scripts/apply-identity-hs256-key-removal-env.sh
```

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
- 同域 Identity 公开入口：`http://localhost/api/identity/health`、`http://localhost/api/identity/health/dependencies`
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

当前公网身份入口为 `/api/identity/auth/**` 和 `/api/identity/admin/**`；登录、注册、Token 校验、续签、注销、刷新会话查询/撤销、密码、账号资料和应用级角色已经由 `identityApi` 执行。登录和邮箱注册验证会返回 `token` 与 `refreshToken`，其中新签发的 access token 是标准 JWT；生产签名算法为 `RS256/alicia-rs256-20260822035821`。Identity 用户响应会带 `appRoles`，当前云盘权限使用 `appRoles.cloud`，RAG 角色使用 `appRoles.rag`；全局 `ADMIN` 始终等效为 `CLOUD_ADMIN` 和 `RAG_ADMIN`，普通账号默认等效为 `CLOUD_USER` 和 `RAG_USER`。`iss`、`aud`、`kid` 和算法由 `ALICIA_AUTH_TOKEN_ISSUER`、`ALICIA_AUTH_TOKEN_AUDIENCE`、`ALICIA_AUTH_TOKEN_KEY_ID`、`ALICIA_AUTH_TOKEN_ALGORITHM` 配置，RS256 公钥发布在 `/api/identity/.well-known/jwks.json`。验签支持当前 key 和配置的历史 HS256/RSA JWT key，历史 key 只在密钥轮换窗口中保留；旧两段式 access token 已不再接受。续签接口必须使用 JSON 请求体中的 `refreshToken` 轮换会话，不再支持 Authorization-only 续签。`CloudStorageApi` 负责补齐云盘资料，并通过 `/api/cloud-profile/**` 返回云盘聚合资料、头像和主页背景；CloudStorageApi 会对 RS256 access token 做本地 JWKS 预验签，默认缓存 JWKS 300 秒，可用 `ALICIA_IDENTITY_TOKEN_PREFLIGHT_ENABLED` 和 `ALICIA_IDENTITY_TOKEN_JWKS_CACHE_SECONDS` 调整。全局角色、应用角色、账号状态、`tokenVersion` 和 refresh session 仍由 Identity 的 `/api/identity/auth/me` 做强一致确认；CloudStorageApi 会把入口校验得到的身份快照放入请求上下文，云盘当前用户资料和头像上传复用该快照，避免同一请求内重复调用 Identity。为降低同一 access token 在连续云盘请求中的 Identity 往返，CloudStorageApi 默认启用 3 秒当前用户快照短缓存，缓存命中仍会执行本地 JWT 预验签，TTL 最长限制 30 秒，可用 `ALICIA_IDENTITY_CURRENT_USER_CACHE_ENABLED`、`ALICIA_IDENTITY_CURRENT_USER_CACHE_TTL_SECONDS` 和 `ALICIA_IDENTITY_CURRENT_USER_CACHE_MAX_ENTRIES` 调整。CloudStorageApi 调用 Identity 的默认连接超时为 2 秒，读取超时为 5 秒，可用 `ALICIA_IDENTITY_API_CONNECT_TIMEOUT_MS` 和 `ALICIA_IDENTITY_API_READ_TIMEOUT_MS` 调整；`/api/health/dependencies` 会暴露 Cloud 调用 Identity 的固定操作观测，例如 `auth.me`、`auth.me.cacheHit`、`jwks.fetch`、`admin.listUsers` 的成功/失败/总计数、连续失败数、最近成功/失败时间、最近/平均/最大耗时和脱敏失败分类，同时暴露 `currentUserCache.enabled/ttlMillis/maxEntries/size`，不记录 token 或用户标识。

RAG 执行入口已经消费 Identity 应用角色：`/rag/api/assistant/plan`、`/rag/api/assistant/plan/stream` 和旧兼容入口 `/rag/api/intent/recognize` 会先调用 Identity `/api/identity/auth/me` 校验 `appRoles.rag`，只允许 `RAG_USER` 或 `RAG_ADMIN` 继续执行语义规划；`/rag/api/assistant/auth/access` 是轻量访问权探针，用于部署验证，不触发模型调用。`/rag/api/assistant/contracts/**` 暴露动作模板、能力表和调试契约，只允许 `RAG_ADMIN` 访问，正式客户端应内置版本化 allowlist 而不是让普通用户运行时读取内部契约。RAG 调用 Identity 的默认连接超时为 2 秒，读取超时为 5 秒，可用 `ALICIA_RAG_IDENTITY_API_BASE_URL`、`ALICIA_RAG_IDENTITY_API_CONNECT_TIMEOUT_MS` 和 `ALICIA_RAG_IDENTITY_API_READ_TIMEOUT_MS` 调整。

RAG 依赖健康入口为 `/rag/api/health/dependencies`，会探测 Identity `/api/identity/health` 和 CloudStorageApi `/api/health`，并暴露 RAG 到 Identity/Storage 的脱敏操作观测，例如 `identity.health`、`identity.auth.me`、`storage.health`、`storage.nodes`、`storage.folders` 的成功/失败次数、连续失败数、最近耗时和脱敏失败分类；不记录 token、账号或文件名。

`identityApi` 已启用独立 Flyway，迁移文件位于 `identityApi/src/main/resources/db/identity-migration`，迁移历史表为 `identity_flyway_schema_history`。CloudStorageApi 早期 V1-V17 历史迁移继续保留，其中 V15 删除 `sys_user` 上旧云盘画像字段，V16 删除云盘业务表到身份表的数据库外键，V17 删除云盘库中的身份表残留；Identity V2 将身份表重命名为 `identity_user`，Identity V3 新增 `identity_user_app_role`，用于保存应用级角色。后续身份表结构变更应新增到 `identityApi`。CloudStorageApi 和 identityApi 测试中已有双向迁移边界检查，防止新的身份结构变更写回云盘迁移目录，也防止云盘业务结构进入 Identity 迁移目录；`deploy/scripts/check-identity-route-boundary.sh` 同时检查运行时代码不再引用旧 `sys_user` 表。

`identityApi` 通过 `ALICIA_IDENTITY_MYSQL_DATABASE` 指向独立 MySQL database，`.env.example` 默认使用 `alicia_identity`；MySQL 首次初始化会通过 `deploy/mysql/init-identity-database.sh` 创建该库。生产已完成身份库拆分，当前云盘库不再包含 `sys_user`、`identity_user`、身份验证码、refresh token、审计和 Identity Flyway 历史表。老环境可使用 `deploy/scripts/apply-identity-database-split.sh` 复制身份表和 `identity_flyway_schema_history` 到目标库、备份并更新 `.env`、重启 identity 并运行统一验证；拆库验证通过后，可运行 `deploy/scripts/drop-cloud-identity-residue.sh` 备份并删除云盘库中残留的身份表。

普通云盘 Web 只保留个人云盘体验：个人身份会话和密码能力走同域 Identity 公开入口 `/api/identity/auth/**`，头像上传、头像访问、当前用户云盘聚合资料由 `CloudStorageApi` 的 `/api/cloud-profile/me`、`/api/cloud-profile/avatar` 和 `/api/cloud-profile/avatar/{userId}` 提供；`/api/storage/overview`、`/api/storage/usage-history` 和上传容量校验始终按当前账号自己的云盘额度计算，`CLOUD_ADMIN` 只授予后台权限，不再让普通云盘入口切换成全站视角或个人空间无限额。身份后台位于主站仓库 `mainSite/userSite`，默认页面入口为 `/console/identity/users`，承接 `/api/identity/admin/**` 的用户、应用角色、会话和审计管理。云盘运营后台位于本仓库 `sysManage`，默认页面入口为 `/console/cloud/users`，管理员云盘聚合用户列表统一为 `/api/admin/cloud-users`，云盘容量调整为 `/api/admin/cloud-users/{userId}/quota`；云盘容量、活跃节点、回收站、分享和分片上传会话的后台运营总览由 `/api/admin/cloud-operations/overview` 提供，分享明细、回收站明细和用户容量明细分别由 `/api/admin/cloud-operations/shares`、`/api/admin/cloud-operations/trash`、`/api/admin/cloud-operations/users/storage` 提供。云盘管理员判断使用 Identity 返回的 `appRoles.cloud=CLOUD_ADMIN`。

`sysManage` 没有独立的 `sysManageApi` 后端；第一阶段仍由本仓库 `CloudStorageApi` 承接云盘运营接口。当前对应关系为：用户与额度管理走 `/api/admin/cloud-users` 和 `/api/admin/cloud-users/{userId}/quota`，运营总览、分享、回收站和用户容量明细走 `/api/admin/cloud-operations/**`，APK 后台发布走 `/api/admin/app-package`，APK 公开查询与下载走 `/api/app-package/**`，当前管理员云盘资料走 `/api/cloud-profile/me`。`/api/admin/**` 统一由 CloudStorageApi 的 `AdminPrincipalInterceptor` 保护，权限来自 Identity 的全局 `ADMIN` 或应用角色 `appRoles.cloud=CLOUD_ADMIN`。

云盘 Web 会在启动和运行中通过 `/api/identity/auth/token/refresh` 续签登录态，续签和本地 session 保存都要求同时存在 access token 与 refresh token；未登录或 token 过期时生成规范化的 `/cloudPan/...` returnTo 并跳转主站 `/login`，过期场景会追加 `reason=session-expired` 供主站登录页展示正式提示，同时规避回到登录页造成的跳转环。主动退出登录时调用 `/api/identity/auth/logout` 后再清理本地 token 和 refresh token。云盘 Web 与主站使用同一组浏览器 session key，通过浏览器 storage 事件同步跨标签页登录、续签、过期和退出，并通过共享 session revision 事件同步昵称、头像、云盘背景等资料变更。Android 客户端恢复会话、保存会话和退出登录也使用同一套 Identity 接口与 token 契约；启动续签失败、缺失 refresh token 或运行中收到 401 时会走本地会话过期清理并提示重新登录，不再混用用户主动退出登录提示。默认 logout 只撤销当前刷新会话；需要全设备退出时请求体传 `{"allDevices":true}`。`GET /api/identity/auth/sessions` 返回当前账号刷新会话元数据，不暴露 refresh token 或 token hash；`DELETE /api/identity/auth/sessions/{sessionId}` 只允许撤销当前账号自己的会话。云盘 Web 头像菜单已提供“登录会话”入口，可查看会话并撤销非当前有效会话。

`identityApi` 新注册用户首次携带 identity token 访问 CloudStorageApi 受保护接口时，CloudStorageApi 会自动补建对应的 `cloud_user_profile`，云盘默认额度取 `ALICIA_STORAGE_DEFAULT_USER_QUOTA_BYTES`。

### HTTPS 部署（已签发证书后）

如果你已经拿到 Nginx 证书文件，可以把证书和私钥分别放到：

- `deploy/certs/fullchain.pem`
- `deploy/certs/privkey.pem`

然后使用 HTTPS 覆盖配置启动前端：

```powershell
docker compose -f compose.yaml -f compose.https.yaml up -d --build frontend
```

这样会额外开放 `443`，并将 `http://` 请求自动跳转到 `https://`。统一登录入口为 `https://windwindwind-alicia.cn/login`，云盘 Web 入口为 `https://windwindwind-alicia.cn/cloudPan/`，云盘运营后台默认入口为 `https://windwindwind-alicia.cn/console/cloud/users`，身份后台默认入口为 `https://windwindwind-alicia.cn/console/identity/users`，Android App Links 入口为 `https://windwindwind-alicia.cn/.well-known/assetlinks.json`，正式 RAG 入口为 `https://windwindwind-alicia.cn/rag`，SSE 请求由 Nginx 直通 `rag` 容器；Identity 公开入口为 `https://windwindwind-alicia.cn/api/identity/health`、`/api/identity/health/dependencies`、`/api/identity/.well-known/jwks.json`、`/api/identity/auth/**` 和 `/api/identity/admin/**`。`/rag/internal/` 与 `/api/identity/internal/**` 不对公网开放。

生产服务器更新 RAG 与 Nginx 时，在仓库内执行：

```bash
bash deploy/scripts/update-rag-production.sh
```

脚本会拒绝覆盖服务端已有的 tracked 改动，确认 `.env` 已配置 DeepSeek，快进拉取 `main`，重建 `rag` 与 `frontend`，最后同时检查 `127.0.0.1:8091/api/health` 和公网 `/rag/api/health`。密钥只保留在服务器 `.env`，不会输出到日志。

常规生产更新推荐使用标准发布脚本，减少手动复制多段命令：

生产验证、巡检、备份和发布验收命令已经集中整理在 `docs/production-verification-scripts.md`。

```bash
bash deploy/scripts/update-cloud-production.sh
```

默认会在 `~/aliciaCloudStorage` 内拒绝覆盖 tracked 本地改动，快进拉取 `gitee/main`，确保 `alicia_gateway` 网络存在，重建 `api frontend`，并连续运行统一路由验证、Identity 旧路由边界检查、前端控制台边界检查和后端 API 边界检查。前端边界检查会确认普通云盘端没有后台入口、云盘后台没有身份管理实现，并确认云盘用户端与云盘后台都保留 bundle size 守卫和显式 vendor 分包。`sysManage` 与 `CloudStorageApi` 响应契约联动的更新（例如运营明细 `appRoles` 角色标签）至少使用 `api frontend`；纯前端文案或样式更新才建议显式传 `frontend`。需要指定更多服务时可追加服务名，例如：

```bash
bash deploy/scripts/update-cloud-production.sh api identity frontend
```

大版本发布推荐把备份和发布后巡检一起打开：

```bash
ALICIA_BACKUP_BEFORE_UPDATE=true ALICIA_COLLECT_STATUS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api identity rag frontend
```

如果这次改动涉及云盘文件、分享、上传下载或回收站链路，可把深度生产流验收也打开：

```bash
ALICIA_VERIFY_PRODUCTION_FLOWS_AFTER_UPDATE=true \
  bash deploy/scripts/update-cloud-production.sh api frontend
```

深度流验收会复用登录凭证，额外运行云盘文件操作专项和分享专项；更新脚本已经跑过的总路由和静态边界检查不会重复执行。

如果主站和云盘都要一起更新，可在 `~/aliciaCloudStorage` 内执行：

```bash
bash deploy/scripts/update-main-and-cloud-production.sh
```

当前前端管理台拆分发布优先使用这个联合入口：它会先更新 `~/mainSite` 的主站门户和 `/console/identity/` 身份后台，再更新本仓库的 `/cloudPan/` 普通云盘和 `/console/cloud/` 云盘后台，并运行云盘/Identity/RAG 统一验证、两侧前端边界检查和后端 API 边界检查；只要云盘侧参与更新，主站的公网网关检查默认延后到云盘 `frontend` 更新完成后再执行，即使设置 `ALICIA_SKIP_MAIN_SITE_UPDATE=true` 只更新云盘，也会在主站验证脚本可用时补跑最终主站公网复核，避免旧网关还未发布或云盘网关未接住主站/控制台路径时漏报。可用 `ALICIA_SKIP_MAIN_SITE_UPDATE=true` 或 `ALICIA_SKIP_CLOUD_UPDATE=true` 临时跳过其中一侧；如需强制在主站更新阶段就检查公网网关，可设置 `ALICIA_VERIFY_MAIN_SITE_PUBLIC_DURING_JOINT_UPDATE=true`；如需跳过最后一次主站公网复核，可设置 `ALICIA_SKIP_FINAL_MAIN_SITE_VERIFY=true`。联合发布也支持 `ALICIA_COLLECT_STATUS_AFTER_UPDATE=true`，快照会在最终主站公网复核之后生成，避免只记录到中间发布态。

日常巡检或发布后复核可使用非交互式生产状态快照脚本：

```bash
bash deploy/scripts/collect-production-status.sh
```

该脚本会汇总云盘仓库和 `~/mainSite` 的 Git 版本、tracked 文件状态、Compose 容器、磁盘与 Docker 占用、Cloud/Identity/RAG 直连与前端健康、主站/云盘/控制台入口探针、`/console`、`/cloudPan`、`/console/cloud` 和旧 `/cloudPan/login` 的规范化跳转、三侧依赖健康 JSON、Identity 审计日志脱敏摘要、Identity Flyway 历史、云盘库身份残留边界和 COS 对象清理补偿队列摘要。默认不要求输入账号密码；如需在快照中追加完整登录链路验证，可设置 `ALICIA_STATUS_RUN_ROUTE_VERIFY=true`，此时会先跑主站路由验证再跑云盘/Identity/RAG 统一路由验证；如需追加静态边界检查可设置 `ALICIA_STATUS_RUN_BOUNDARY_CHECK=true`，此时会同时跑主站前端边界、云盘前端边界和后端 API 边界检查。生产更新脚本也支持 `ALICIA_COLLECT_STATUS_AFTER_UPDATE=true bash deploy/scripts/update-cloud-production.sh` 或 `ALICIA_COLLECT_STATUS_AFTER_UPDATE=true bash deploy/scripts/update-main-and-cloud-production.sh` 在更新后自动生成快照。

大更新或迁移前建议先生成一次只读生产备份：

```bash
bash deploy/scripts/backup-production-data.sh
```

备份脚本会用 `mysqldump --single-transaction` 分别导出云盘库和 Identity 库，并把 `.env`、TLS 证书和 `deploy/generated/identity-rs256/` 下的签名密钥材料打包到 `deploy/generated/production-backups/<timestamp>/`。该目录被 git 忽略，脚本只打印文件路径，不输出密钥或配置内容。备份生成后默认会运行 `validate-production-backup.sh` 校验 `SHA256SUMS`、gzip dump、敏感配置 tar 和 manifest 关键字段；也可以手动执行 `bash deploy/scripts/validate-production-backup.sh` 校验最新备份。可用 `ALICIA_BACKUP_INCLUDE_ENV=false`、`ALICIA_BACKUP_INCLUDE_CERTS=false`、`ALICIA_BACKUP_INCLUDE_GENERATED_KEYS=false` 或 `ALICIA_VALIDATE_BACKUP_AFTER_CREATE=false` 调整备份/校验行为。`update-cloud-production.sh` 设置 `ALICIA_BACKUP_BEFORE_UPDATE=true` 时，会在重建容器前自动执行该备份脚本。

生产更新 `api`、`identity`、`rag` 或前端路由后，可以使用统一回归脚本检查主域路径边界、主站 `/login`、Android App Links 根路径、云盘 `/cloudPan` 规范化跳转、身份后台 `/console/identity/users`、`/console/identity/roles`、`/console/identity/sessions` 与 `/console/identity/audit`、云盘后台 `/console/cloud/users`、`/console/cloud/operations` 与 `/console/cloud/app-package`、`/cloudPan/login` 到统一登录的交接、CloudStorageApi 到 Identity 的依赖健康、Identity 数据库/Flyway 依赖健康、RAG 到 Identity/Storage 的依赖健康和 telemetry、登录续签、JWT `alg/iss/aud/kid` 元数据、JWKS 入口、应用级 `cloud` 与 `rag` 角色、RAG 访问权探针、RAG 内部契约 `RAG_ADMIN` 边界、刷新会话查询和指定撤销、云盘聚合资料、存储概览、管理员云盘用户入口、管理员云盘运营总览和分享/回收站/用户容量明细、CloudStorageApi COS 对象清理补偿表、Identity 应用角色与审计日志查询、会话撤销审计事件写入、Identity Flyway 迁移历史、旧身份路径 404、注销失效和审计日志最新行；生产 RS256 模式下，CloudStorageApi 会先用 Identity JWKS 对 access token 做本地预验签，再调用 Identity 做强一致状态确认：

```bash
bash deploy/scripts/verify-identity-cloud-routes.sh
```

脚本默认验证 `http://127.0.0.1:8090`、`http://127.0.0.1:8093` 和 `https://127.0.0.1`；如需改为域名验证，可设置 `ALICIA_PUBLIC_BASE_URL=https://windwindwind-alicia.cn`。完整管理员检查需要使用管理员账号；普通账号可临时设置 `ALICIA_VERIFY_SKIP_ADMIN_CHECK=true`。

需要一次性跑总路由、云盘文件操作、分享链路和静态边界，可执行：

```bash
bash deploy/scripts/verify-cloud-production-flows.sh
```

该脚本只做验收，不重建容器；它会读取一次账号密码，并传给后续专项脚本。

需要深入回归真实分享链路时，可执行：

```bash
bash deploy/scripts/verify-cloud-share-flow.sh
```

该脚本会创建临时文件夹和文本文件，验证分享状态、提取码、详情、下载 URL、直连下载、HTTP Range 断点下载、文件夹 ZIP、保存到网盘和撤销，并默认清理测试数据。

需要深入回归云盘文件操作基础盘时，可执行：

```bash
bash deploy/scripts/verify-cloud-storage-flow.sh
```

该脚本会创建临时文件夹和文本文件，验证概览、上传、列表、下载 URL、直连下载、HTTP Range 断点下载、文件夹 ZIP、重命名、移动、批量移入回收站、批量恢复、单项删除到回收站、彻底删除和测试数据清理。

提交或部署前也可以先做一次静态路径边界检查，防止源码和部署配置重新引入旧 `/api/auth/**`、旧 `/api/admin/users`，或让 Identity 重新引用云盘画像字段：

```bash
bash deploy/scripts/check-identity-route-boundary.sh
```

该脚本优先使用 `rg`，服务器未安装 `rg` 时会自动降级到 `grep`。

同一边界也已纳入 Maven 测试：`CloudStorageApi` 的 `IdentityRouteBoundaryTest` 会扫描 Web、Android、CloudStorageApi、identityApi、RAG 和 deploy 源码目录，防止旧身份路径回流；`CloudApiRouteOwnershipTest` 和 `IdentityApiRouteOwnershipTest` 会检查后端 Controller 的 API 前缀归属，确保 `CloudStorageApi` 不暴露身份路由、`identityApi` 不暴露云盘业务路由。`IdentityApiRouteOwnershipTest` 也会检查身份后台 Controller 是否传递 Authorization，并确保其委托的管理服务方法直接调用 `requireAdminUser`；`CurrentPrincipalTest` 固定云盘后台权限口径：全局管理员和 `cloud/CLOUD_ADMIN` 可访问，其他应用管理员不可访问。

本地提交前也可以一次运行后端边界测试：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\scripts\verify-backend-api-boundaries.ps1
```

服务器或 Linux 环境可执行：

```bash
bash deploy/scripts/verify-backend-api-boundaries.sh
```

移动端发版前可以执行 Android readiness 检查，脚本会扫描 `phoneApp` 和 `phoneAppAdd` 的正式服务入口、旧 Identity 路径、旧测试服入口、refresh token 契约、401 过期会话处理，并默认运行两个 Android 工程的 Debug 单测：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/check-android-release-readiness.ps1
```

只做快速静态检查时可追加 `-SkipGradle`。

生成可上传的 Android APK 发版包时，优先使用仓库级准备脚本。它默认构建当前新视觉 `phoneAppAdd` 的 Release APK，并使用正式包名 `com.alicia.cloudstorage.phone` 作为对外下载 APK，复制到 `deploy/generated/android-release-packages/`，写入 SHA-256、`manifest.json`、`release-notes.txt` 和管理员上传 helper：

首次正式发版前先生成 Android release keystore，并妥善备份生成目录：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/generate-android-release-keystore.ps1
```

之后在同一终端加载生成目录里的 `android-release-signing.env.ps1`，再准备 signed APK：

```powershell
. deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/prepare-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

也可以直接使用一键发布脚本完成“打包、签名校验、管理员登录、上传到服务器、公开下载入口验证”：

```powershell
. deploy/generated/android-signing/<timestamp>/android-release-signing.env.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/publish-android-release-package.ps1 -ReleaseNotes "填写本次正式更新说明"
```

如果希望 APK 跟随服务器 `git pull` 自动发布，先把已准备好的 signed APK 整理到 Git 当前发布目录：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/stage-android-git-package.ps1
```

该脚本会把最新 `phoneAppAdd` release 产物复制为 `deploy/android-app-package/current.apk`，并同步写入 `version-name.txt`、`release-notes.txt` 和 `current.apk.sha256`。提交推送这些文件后，服务器执行 `update-cloud-production.sh` 时会在默认 `auto` 模式下检测到 Git APK 产物，并自动调用 `/api/admin/app-package` 发布；服务器当前已是同一 `versionName` 时会跳过重复上传，需要强制重传可设置 `ALICIA_ANDROID_APP_PACKAGE_FORCE=true`。

生成目录被 git 忽略。Release 包默认必须完成签名；如果没有加载签名配置，脚本会拒绝把 unsigned APK 当作正式包。`publish-android-release-package.ps1` 默认发布 `phoneAppAdd` 到 `/api/admin/app-package`，没有传入 `ALICIA_ADMIN_TOKEN` 时会提示输入 Identity 管理员账号和密码，并在上传后校验 `/api/app-package/version` 和 `/api/app-package/download/current`。旧 `phoneApp` 仅作为历史版本参考，不再作为默认下载 APK 来源。

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
$env:ALICIA_IDENTITY_API_CONNECT_TIMEOUT_MS="2000"
$env:ALICIA_IDENTITY_API_READ_TIMEOUT_MS="5000"
$env:ALICIA_IDENTITY_TOKEN_PREFLIGHT_ENABLED="true"
$env:ALICIA_IDENTITY_TOKEN_JWKS_CACHE_SECONDS="300"
$env:ALICIA_IDENTITY_CURRENT_USER_CACHE_ENABLED="true"
$env:ALICIA_IDENTITY_CURRENT_USER_CACHE_TTL_SECONDS="3"
$env:ALICIA_IDENTITY_CURRENT_USER_CACHE_MAX_ENTRIES="1024"
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

Set-Location ../sysManage
npm ci
npm run dev -- --host 127.0.0.1 --port 5174
```

前端开发地址：

- `http://localhost:5173/cloudPan/`
- `http://localhost:5174/console/cloud/users`

Windows 本地可以直接跑前端拆分验收脚本，它会构建 `webApp` 和 `sysManage`，并检查普通云盘、云盘后台的 Docker/Nginx 挂载、`returnTo`、session 同步、边界守护、bundle size 守卫和 vendor 分包：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\scripts\verify-frontend-split-local.ps1
```

如果要一次性验收主站门户、身份后台、普通云盘和云盘后台四个前端，可在本仓库执行平台级本地验收脚本。它会默认使用同级 `..\mainSite` 仓库，也可通过 `ALICIA_MAIN_SITE_PROJECT_DIR` 或参数指定主站路径：

```powershell
powershell -ExecutionPolicy Bypass -File deploy\scripts\verify-platform-frontend-split-local.ps1
```

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
npm run audit:high
npm run build

Set-Location ../sysManage
npm run audit:high
npm run build
```

两个前端的 `npm run build` 都会先检查统一登录 `returnTo`、浏览器 session 同步和职责边界，再执行 TypeScript 与 Vite 生产构建，最后校验单个 JS chunk 不超过 500 KiB，避免前端依赖升级后重新出现大包警告。

停止容器：

```powershell
docker compose down
```

