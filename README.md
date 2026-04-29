# Alicia Cloud Storage

一个基于 Spring Boot + React + MySQL + 腾讯云 COS 的轻量云盘项目，支持账号权限、文件管理、回收站、大文件分片上传、背景图个性化和管理员配额管理。

## 项目特点

- 普通用户不能自助注册，由管理员统一创建账号
- 支持登录鉴权、个人资料修改、密码修改
- 文件二进制内容存储在腾讯云 COS，MySQL 存储文件和文件夹元数据
- 支持文件列表、搜索、类型筛选、分页、排序
- 支持新建文件夹、上传、下载、重命名、移动
- 支持回收站、批量移动、批量删除、批量恢复、彻底删除
- 支持图片、PDF、文本、音视频预览
- 支持普通上传和大文件分片上传
- 支持断点续传、上传队列、总进度、单文件进度、失败继续、取消当前上传
- 普通用户按个人配额校验上传空间，管理员账号不受个人配额限制
- 支持用户头像和主页背景图上传，并与账号关联

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
├─ CloudStorageDB/       # 早期 SQL 初始化脚本
├─ webApp/               # React + Vite 前端
├─ phoneApp/             # 移动端占位目录
├─ compose.yaml          # 本地 / 首发用 Docker Compose
├─ .env.example          # 环境变量示例
└─ pom.xml               # Maven 根工程
```

## 运行前准备

建议准备以下环境：

- JDK 21
- Node.js 20+ 和 npm
- Docker Desktop 或 Docker Engine + Docker Compose Plugin
- 可用的腾讯云 COS 密钥、地域和桶名

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

## 最快启动方式

当前仓库最省事的本地启动方式是先构建前后端产物，再用 Docker Compose 统一拉起。

### 1. 构建后端 JAR

Windows PowerShell：

```powershell
.\mvnw -pl CloudStorageApi -DskipTests package
```

macOS / Linux：

```bash
./mvnw -pl CloudStorageApi -DskipTests package
```

### 2. 构建前端静态资源

```powershell
Set-Location webApp
npm ci
npm run build
Set-Location ..
```

### 3. 启动容器

```powershell
docker compose up -d --build
```

启动后默认地址：

- 前端：`http://localhost:8091`
- 后端：`http://localhost:8090`
- 健康检查：`http://localhost:8090/api/health`
- MySQL：`localhost:3310`

查看运行状态：

```powershell
docker compose ps
docker compose logs -f api
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
$env:ALICIA_AUTH_TOKEN_SECRET="replace-this-with-a-long-random-secret"
$env:ALICIA_COS_SECRET_ID="your-secret-id"
$env:ALICIA_COS_SECRET_KEY="your-secret-key"
$env:ALICIA_COS_REGION="ap-shanghai"
$env:ALICIA_COS_BUCKET="your-bucket-appid"

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

- `http://localhost:5173`

## 默认演示账号

当数据库为空时，后端会自动初始化演示账号：

- 管理员：`13800000000` / `Admin@123`
- 普通用户：`13900000000` / `User@123`

这些账号仅适合本地体验或内测环境。上线前请务必修改密码，或关闭这段初始化逻辑。

## 常用命令

后端测试：

```powershell
.\mvnw -pl CloudStorageApi test
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

## Docker 说明

当前 Dockerfile 是运行时镜像，不会在镜像构建时自动编译源码。

这意味着在执行 `docker compose up -d --build` 之前，需要先准备好：

- `CloudStorageApi/target/*.jar`
- `webApp/dist/*`

如果缺少这两部分，容器构建会失败。这一点在首次部署到新服务器时尤其要注意。

## License

当前仓库暂未附带开源许可证。如需公开开源，建议补充 `MIT`、`Apache-2.0` 或其他明确许可证。
