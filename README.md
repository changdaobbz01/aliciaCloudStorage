# Alicia Cloud Storage

一个基于 Spring Boot + React + MySQL + 腾讯云 COS 的轻量云盘项目，支持账号权限、文件管理、回收站、大文件分片上传、背景图个性化和管理员配额管理。

## 项目特点

- 普通用户不能自助注册，由管理员统一创建账号
- 支持登录鉴权、个人资料修改、密码修改、管理员重置他人密码
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
├─ CloudStorageApi/      # Spring Boot 后端
├─ identityApi/          # 统一身份服务骨架，默认不接生产流量
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

启动后默认地址：

- 前端：`http://localhost/cloudPan/`
- 主域名根入口：`http://localhost/` 会代理到工具主站容器
- 统一登录入口：`http://localhost/login`
- 后端（仅本机回环）：`http://127.0.0.1:8090`
- 健康检查（仅本机回环）：`http://127.0.0.1:8090/api/health`
- RAG（仅本机回环）：`http://127.0.0.1:8091`
- 同域 RAG 入口：`http://localhost/rag/api/health`
- Identity API 骨架默认不启动；需要单独验证时使用 `--profile identity`
- MySQL（仅本机回环）：`127.0.0.1:3310`

查看运行状态：

```powershell
docker compose ps
docker compose logs -f api
```

单独验证 Identity API 骨架：

```powershell
docker compose --profile identity up -d --build identity
curl http://127.0.0.1:8093/api/identity/health
curl http://127.0.0.1:8093/api/identity/internal/users/1
```

当前生产流量仍由 `CloudStorageApi` 处理登录、注册、Token 和账号资料；`identityApi` 只用于后续拆分验证，当前只读连接现有身份表，不接管写流量。

### HTTPS 部署（已签发证书后）

如果你已经拿到 Nginx 证书文件，可以把证书和私钥分别放到：

- `deploy/certs/fullchain.pem`
- `deploy/certs/privkey.pem`

然后使用 HTTPS 覆盖配置启动前端：

```powershell
docker compose -f compose.yaml -f compose.https.yaml up -d --build frontend
```

这样会额外开放 `443`，并将 `http://` 请求自动跳转到 `https://`。统一登录入口为 `https://windwindwind-alicia.cn/login`，云盘 Web 入口为 `https://windwindwind-alicia.cn/cloudPan/`，正式 RAG 入口为 `https://windwindwind-alicia.cn/rag`，SSE 请求由 Nginx 直通 `rag` 容器；`/rag/internal/` 不对公网开放。

生产服务器更新 RAG 与 Nginx 时，在仓库内执行：

```bash
bash deploy/scripts/update-rag-production.sh
```

脚本会拒绝覆盖服务端已有的 tracked 改动，确认 `.env` 已配置 DeepSeek，快进拉取 `main`，重建 `rag` 与 `frontend`，最后同时检查 `127.0.0.1:8091/api/health` 和公网 `/rag/api/health`。密钥只保留在服务器 `.env`，不会输出到日志。

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
$env:ALICIA_AUTH_TOKEN_SECRET="replace-this-with-a-long-random-secret"
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

